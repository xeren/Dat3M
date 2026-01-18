package com.dat3m.dartagnan.verification.solving;

import com.dat3m.dartagnan.configuration.GenMCReasoning;
import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.core.Store;
import com.dat3m.dartagnan.program.processing.ConsecutiveGotoSimplification;
import com.dat3m.dartagnan.program.processing.IdReassignment;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.Timer;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.GenMCExecutionModel;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.axiom.Axiom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.*;

import static com.dat3m.dartagnan.configuration.OptionNames.*;
import static com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator.ExprEvaluationContext;
import static com.dat3m.dartagnan.verification.model.GenMCExecutionModel.*;
import static com.dat3m.dartagnan.utils.Timer.formatTime;

import java.util.*;


/*
    Refinement is a custom solving procedure that starts from a weak memory model (possibly the empty model)
    and iteratively refines it to perform a verification task.
    It can be understood as a lazy offline-SMT solver.
    More concretely, it iteratively
        - finds some assertion-violating execution w.r.t. to some (very weak) baseline memory model
        - checks the consistency of this execution using a custom theory solver (CAAT-Solver)
        - refines the used memory model if the found execution was inconsistent, using the explanations
          provided by the theory solver.
 */
@Options
public final class GenMCSolver extends ModelChecker {

    private static final Logger logger = LogManager.getLogger(GenMCSolver.class);
    
    @Option(
            name=DELAY_CONSISTENCY_CHECKS,
            description = "Delay the consistency checks by skipping them for backward revisits and coherence",
            secure = true)
    boolean delayConsistencyChecks = false;
    
    @Option(
            name=SHOW_CONSISTENT_EXECUTIONS,
            description = "Check consistency for every complete execution to show the number of consistent executions with GenMC",
            secure = true)
    boolean showConsistentExecutions = false;
    
    @Option(
            name=REASONING,
            description = "Reasoning method to learn during exploration",
            secure = true)
    GenMCReasoning reasoningMethod = GenMCReasoning.getDefault();

    @Option(
            name=UNDERAPPROXIMATE_WMM,
            description = "Only check full consistency when property needs to be checked",
            secure = true)
    boolean underapproximateWmm = false;
    
    @Option(
            name=USE_SMT_SOLVER,
            description = "Use SMT solver to resolve the data flow with GenMC",
            secure = true)
    boolean useSMTSolver = false;

    private int consistentExecutions = 0;
    private int completeExecutions = 0;
    private int failedExecutions = 0;
    private int blockedExecutions = 0;

    // ================================================================================================================
    // GenMC solver

    private GenMCSolver(VerificationTask task) {
        super(task);
    }

    //TODO: We do not yet use Witness information. The problem is that WitnessGraph.encode() generates
    // constraints on hb, which is not encoded in Refinement.
    //TODO (2): Add possibility for Refinement to handle CAT-properties (it ignores them for now).
    public static GenMCSolver run(final SolverContext ctx, final ProverEnvironment prover, final VerificationTask task)
            throws InterruptedException, SolverException, InvalidConfigurationException {
        final GenMCSolver solver = new GenMCSolver(task);
        task.getConfig().inject(solver);
        logger.info("{}: {}", DELAY_CONSISTENCY_CHECKS, solver.delayConsistencyChecks);
        logger.info("{}: {}", SHOW_CONSISTENT_EXECUTIONS, solver.showConsistentExecutions);
        logger.info("{}: {}", REASONING, solver.reasoningMethod);
        logger.info("{}: {}", UNDERAPPROXIMATE_WMM, solver.underapproximateWmm);
        logger.info("{}: {}", USE_SMT_SOLVER, solver.useSMTSolver);
        solver.runInternal(ctx, prover);
        return solver;
    }

    private void runInternal(final SolverContext ctx, final ProverEnvironment prover)
            throws InterruptedException, SolverException, InvalidConfigurationException {
        final Program program = task.getProgram();
        final Wmm memoryModel = task.getMemoryModel();
        final Context analysisContext = Context.create();
        final Configuration config = task.getConfig();

        // ------------------------ Preprocessing / Analysis ------------------------

        // TODO: This is a reasonable transformation for all methods (eager/lazy), however,
        //  our current processing pipelines (WmmProcessor/ProgramProcessor) are unaware of the property
        //  so we cannot perform property-aware transformation in those pipelines right now.
        removeFlaggedAxiomsIfNotNeeded(task);
        
        memoryModel.configureAll(config);
        preprocessProgram(task, config);
        ConsecutiveGotoSimplification.newInstance().run(program);
        IdReassignment.newInstance().run(program);
        preprocessMemoryModel(task, config);

        performStaticProgramAnalyses(task, analysisContext, config);
        performStaticWmmAnalyses(task, analysisContext, config);

        removePorfAcyclicityAxiom(task);

        // ------------------------ Model ------------------------

        final ExprEvaluationContext evalContext = new ExprEvaluationContext(task, analysisContext, ctx, prover, useSMTSolver);
        final GenMCExecutionModel model = GenMCExecutionModel.withTask(task, analysisContext, evalContext, reasoningMethod, underapproximateWmm);

        // ------------------------ Solving ------------------------

        logger.info("TruSt algorithm started.");
        final Timer genmcTimer = new Timer();
        genmcTimer.start();
        final boolean result = trust(model);
        genmcTimer.stop();
        logger.info("TruSt took {}", genmcTimer.getFormattedTime());

        if (evalContext.neededSMTSolver()) {
            context = evalContext.getTraceEncoder().getEncodingContext();
        }

        final Property.Type propertyType = Property.getCombinedType(task.getProperty(), task);
        if (logger.isInfoEnabled()) {
            final String message = !result ? (propertyType == Property.Type.SAFETY ? "Specification violation found." : "Specification witness found.") :
                        (propertyType == Property.Type.SAFETY ? "Specification proven." : "Specification falsified.");
            logger.info(message);
        }
        res = result ? Result.PASS : Result.FAIL;

        // For Safety specs, we have SAT=FAIL, but for reachability specs, we have
        // SAT=PASS
        res = propertyType == Property.Type.SAFETY ? res : res.invert();
        logger.info("Verification finished with result {}", res);

        if (showConsistentExecutions) {
            logger.info("Number of consistent executions explored: {}", consistentExecutions);
        }
        logger.info("Number of complete executions explored: {}", completeExecutions);
        logger.info("Number of blocked executions found: {}", blockedExecutions);
        logger.info("Number of inconsistent executions found: {}", failedExecutions);
        int incompleteExecutions = failedExecutions + blockedExecutions;
        logger.info("Time per execution estimation: {}", formatTime(
                (genmcTimer.getTime() + (completeExecutions + (incompleteExecutions + 1) / 2) / 2) / (completeExecutions + (incompleteExecutions + 1) / 2)));

        final int solverChecks = model.getSolverChecks();
        final Timer solverCheckTimer = model.getSolverCheckTimer();

        logger.info("Solver checks: {}", solverChecks);
        if (solverChecks != 0) {
            logger.info("Total solver check time: {}", solverCheckTimer.getFormattedTime());
            logger.info("Average solver check time: {}", formatTime((solverCheckTimer.getTime() + solverChecks / 2) / solverChecks));
        }

        final int consistencyChecks = getConsistencyChecks();
        final Timer consistencyCheckTimer = getConsistencyCheckTimer();

        logger.info("Consistency checks: {}", consistencyChecks);
        if (consistencyChecks != 0) {
            logger.info("Total consistency check time: {}", consistencyCheckTimer.getFormattedTime());
            logger.info("Average consistency check time: {}", formatTime((consistencyCheckTimer.getTime() + consistencyChecks / 2) / consistencyChecks));
        }
        if (reasoningMethod.usesReasons()) {
            logger.info("Total reason evaluation time: {}", getReasonEvaluationTimer().getFormattedTime());
        }
        if (reasoningMethod.usesPatterns()) {
            logger.info("Total pattern evaluation time: {}", getPatternEvaluationTimer().getFormattedTime());
        }

        if (evalContext.neededSMTSolver()) {
            logger.info("Total encoding time: {}", evalContext.getTraceEncoder().getEncodingTimer().getFormattedTime());
        }
        logger.info("Total expression evaluation time: {}", model.getEvaluationTimer().getFormattedTime());
    }

    @Override
    public boolean hasModel() {
        return false;
    }

    private static void removeFlaggedAxiomsIfNotNeeded(final VerificationTask task) {
        // We remove flagged axioms if we do not check for them.
        if (!task.getProperty().contains(Property.CAT_SPEC)) {
            List.copyOf(task.getMemoryModel().getAxioms()).stream()
                    .filter(Axiom::isFlagged)
                    .forEach(task.getMemoryModel()::removeConstraint);
        }
    }

    private static void removePorfAcyclicityAxiom(final VerificationTask task) {
        // We remove po | rf acyclicity axioms as GenMC cannot create such executions.
        List.copyOf(task.getMemoryModel().getAxioms()).stream()
                .filter(GenMCSolver::isPorfAcyclicity)
                .forEach(task.getMemoryModel()::removeConstraint);
    }

    private static boolean isPorfAcyclicity(final Axiom axiom) {
        if (axiom.isAcyclicity()) {
            final  String acyclicTerm = axiom.getRelation().getDefinition().getTerm();
            return "po | rf".equals(acyclicTerm) || "rf | po".equals(acyclicTerm);
        }
        return false;
    }

    private boolean trust(final GenMCExecutionModel model) throws SolverException, InterruptedException, InvalidConfigurationException {
        model.initialize();
        return visit(model);
    }

    private boolean visit(final GenMCExecutionModel model) throws SolverException, InterruptedException, InvalidConfigurationException {
        model.resetConsistencyStatus();
        final EventData event = model.nextEvent();
        if (!model.isConsistent()) {
            return inconsistentExecution();
        }
        if (model.hasError()) {
            return false;
        }
        if (event == null) {
            if (model.isFinished()) {
                ++completeExecutions;
                if (showConsistentExecutions && model.checkFullConsistency()) {
                    ++consistentExecutions;
                }
                return model.checkProperties();
            } else {
                ++blockedExecutions;
                logger.debug("Not all threads are finished yet");
                return true;
            }
        }
        if (event.isJump()) {
            return visitJumps(model, event);
        }
        if (event.isRead()) {
            return visitRfs(model, event);
        }
        if (event.isWrite()) {
            final Event write = event.getEvent();
            final Thread currentThread = write.getThread();
            final Collection<EventData> mayCoSet = model.getMayCoSet(event);
            final Map<Thread, Integer> porfPrefix = model.getPorfPrefix();
            final Collection<EventData> relevantReads = model.getMayRfSet(event);
            relevantReads.removeIf(read -> inPorfPrefix(porfPrefix, currentThread, read));
            if ((mayCoSet.size() > 1 || !relevantReads.isEmpty()) && !model.checkConsistency(true)) {
                return inconsistentExecution();
            }
            final GenMCEventDomain trace = model.getTrace();
            final Map<Thread, List<EventData>> threadEventsMap = trace.getThreadEventsMap();
            final Event lastRead = getLastRead(threadEventsMap.get(currentThread), event.getLocalId() - 1);
            if (!visitCos(model, event, mayCoSet, lastRead, false)) {
                return false;
            }
            if (relevantReads.isEmpty()) {
                return true;
            }
            final Set<Map.Entry<Thread, List<EventData>>> threadEventsEntries = threadEventsMap.entrySet();
            final Map<EventData, List<EventData>> writeReadsMap = trace.getWriteReadsMap();
            final Map<Thread, Event> boundEvents = new HashMap<>(model.getOptimalPorfPrefixCapacity() + 1);
            if (lastRead != null) {
                boundEvents.put(currentThread, lastRead);
            }
            for (final EventData read : relevantReads) {
                if (model.hasBacktrackingPoint()) {
                    return true;
                }
                final List<EventData> eventsToDelete = getEventsToDelete(threadEventsEntries, porfPrefix, read, currentThread, boundEvents);
                if (shouldRevisit(currentThread, eventsToDelete, read, porfPrefix, writeReadsMap)) {
                    final Event readEvent = read.getEvent();
                    logger.debug("Backward revisiting load {}: {} for store {}: {}", readEvent.getGlobalId(), readEvent, write.getGlobalId(), write);
                    final Map.Entry<GenMCExecutionModel, EventData> backwardRevisit = getBackwardRevisitModel(model, eventsToDelete, boundEvents, readEvent, write, lastRead);
                    final GenMCExecutionModel backwardRevisitModel = backwardRevisit.getKey();
                    final EventData revisitWrite = backwardRevisit.getValue();
                    if (!visitCos(backwardRevisitModel, revisitWrite, backwardRevisitModel.getMayCoSet(revisitWrite), lastRead, true)) {
                        return false;
                    }
                    backwardRevisitModel.removeRevisitFromPorfPrefixes(readEvent);
                    model.updateBacktrackingPoint(backwardRevisitModel, write);
                }
            }
            return true;
        }
        return visit(model);
    }

    private boolean visitJumps(final GenMCExecutionModel model, final EventData event) throws SolverException, InterruptedException, InvalidConfigurationException {
        if (model.needsJumpRevisit()) {
            final boolean needsNewConsistencyCheck = model.needsNewConsistencyCheck();
            final boolean lastNeedsNewConsistencyCheck = model.lastNeedsNewConsistencyCheck();
            for (final boolean wasExecuted : List.of(event.wasExecuted(), !event.wasExecuted())) {
                model.setJump(event, wasExecuted);
                logger.debug("Visiting non-deterministic jump {}: {} with execution status {}",
                        event.getEvent().getGlobalId(), event.getEvent(), wasExecuted);
                if (!model.isConsistent()) {
                    return inconsistentExecution();
                }
                if (!visit(model)) {
                    return false;
                }
                model.rollback(event, needsNewConsistencyCheck, lastNeedsNewConsistencyCheck);
            }
        }
        return visit(model);
    }

    private boolean visitRfs(final GenMCExecutionModel model, final EventData read) throws SolverException, InterruptedException, InvalidConfigurationException {
        final Collection<EventData> mayRfSet = model.getMayRfSet(read);
        if (!delayConsistencyChecks && mayRfSet.size() > 1 && !model.checkConsistency(true)) {
            return inconsistentExecution();
        }
        final boolean needsNewConsistencyCheck = model.needsNewConsistencyCheck();
        final boolean lastNeedsNewConsistencyCheck = model.lastNeedsNewConsistencyCheck();
        for (final EventData write : mayRfSet) {
            model.setRf(read, write);
            logger.debug("Forward revisiting load {}: {} for store {}: {}", read.getEvent().getGlobalId(),
                    read.getEvent(), write.getEvent().getGlobalId(), write.getEvent());
            if (!visit(model)) {
                return false;
            }
            model.rollback(read, needsNewConsistencyCheck, lastNeedsNewConsistencyCheck);
        }
        return true;
    }

    private boolean visitCos(final GenMCExecutionModel model, final EventData write, final Collection<EventData> mayCoSet, final Event lastRead,
                             final boolean forBackwardRevisit) throws SolverException, InterruptedException, InvalidConfigurationException {
        if (mayCoSet.isEmpty()) {
            model.getTrace().addAddressInit(write);
            return visit(model);
        }
        final boolean needsNewConsistencyCheck = model.needsNewConsistencyCheck();
        final boolean lastNeedsNewConsistencyCheck = model.lastNeedsNewConsistencyCheck();
        for (final EventData otherWrite : mayCoSet) {
            model.setCo(otherWrite, write, mayCoSet);
            logger.debug("Visiting coherence ({}, {}): ({},  {})", otherWrite.getEvent().getGlobalId(),
                    write.getEvent().getGlobalId(), otherWrite.getEvent(), write.getEvent());
            if (!visit(model)) {
                return false;
            }
            model.rollbackCo(otherWrite, write, lastRead, forBackwardRevisit, needsNewConsistencyCheck, lastNeedsNewConsistencyCheck);
        }
        return true;
    }

    private boolean inconsistentExecution() {
        ++failedExecutions;
        logger.debug("Aborting exploration: Inconsistent execution");
        return true;
    }

    private static List<EventData> getEventsToDelete(final Set<Map.Entry<Thread, List<EventData>>> threadEventsMap, final Map<Thread, Integer> porfPrefix, EventData read,
                                                     final Thread currentThread, Map<Thread, Event> boundEvents) {
        int readId = read.getId();
        final List<EventData> eventsToDelete = new ArrayList<>();
        for (final Map.Entry<Thread, List<EventData>> threadData : threadEventsMap) {
            final Thread thread = threadData.getKey();
            if (thread != currentThread) {
                final Integer prefixLimitForThread = porfPrefix.get(thread);
                final List<EventData> threadEvents = threadData.getValue();
                boolean boundFound = false;
                if (prefixLimitForThread == null) {
                    for (int i = threadEvents.size() - 1; i >= 0; --i) {
                        final EventData e = threadEvents.get(i);
                        if (readId < e.getId()) {
                            eventsToDelete.add(e);
                        } else {
                            final Event lastRead = getLastRead(threadEvents, e.getLocalId());
                            if (lastRead != null) {
                                boundEvents.put(thread, lastRead);
                            } else {
                                boundEvents.remove(thread);
                            }
                            boundFound = true;
                            break;
                        }
                    }
                } else {
                    for (int i = threadEvents.size() - 1; i >= 0; --i) {
                        final EventData e = threadEvents.get(i);
                        if (readId < e.getId() && prefixLimitForThread < e.getEvent().getGlobalId()) {
                            eventsToDelete.add(e);
                        } else {
                            final Event lastRead = getLastRead(threadEvents, e.getLocalId());
                            if (lastRead != null) {
                                boundEvents.put(thread, lastRead);
                            } else {
                                boundEvents.remove(thread);
                            }
                            boundFound = true;
                            break;
                        }
                    }
                }
                if (!boundFound) {
                    boundEvents.remove(thread);
                }
            }
        }
        return eventsToDelete;
    }

    private static boolean shouldRevisit(final Thread currentThread, final List<EventData> eventsToDelete, final EventData read, final Map<Thread, Integer> porfPrefix,
                                         final Map<EventData, List<EventData>> writeReadsMap) {
        for (final EventData event : eventsToDelete) {
            if (!isMaximallyAdded(currentThread, event, porfPrefix, writeReadsMap)) {
                return false;
            }
        }
        return isMaximallyAdded(currentThread, read, porfPrefix, writeReadsMap);
    }

    private static boolean isMaximallyAdded(final Thread currentThread, final EventData event, final Map<Thread, Integer> porfPrefix,
                                            final Map<EventData, List<EventData>> writeReadsMap) {
        final EventData maximalEvent;
        final int eventId;
        if (event.getEvent() instanceof Store) {
            eventId = event.getId();
            final List<EventData> reads = writeReadsMap.get(event);
            if (reads != null) {
                for (final EventData read : reads) {
                    if (read.getId() < eventId || inPorfPrefix(porfPrefix, currentThread, read)) {
                        return false;
                    }
                }
            }
            maximalEvent = event;
        } else {
            maximalEvent = event.getReadFrom();
            if (maximalEvent == null) {
                return true;
            }
            eventId = event.getId();
            if (maximalEvent.getId() > eventId && !inPorfPrefix(porfPrefix, currentThread, maximalEvent)) {
                return false;
            }
        }
        EventData coSuccessor = maximalEvent.getCoSuccessor();
        while (coSuccessor != null) {
            if (coSuccessor.getId() < eventId || inPorfPrefix(porfPrefix, currentThread, coSuccessor)) {
                return false;
            }
            coSuccessor = coSuccessor.getCoSuccessor();
        }
        return true;
    }

    private static boolean inPorfPrefix(final Map<Thread, Integer> porfPrefix, final Thread currentThread, final EventData event) {
        final Thread eventThread = event.getThread();
        if (eventThread == currentThread) {
            return true;
        }
        final Integer prefixLimitForThread = porfPrefix.get(eventThread);
        return prefixLimitForThread != null && prefixLimitForThread >= event.getEvent().getGlobalId();
    }

    private static Map.Entry<GenMCExecutionModel, EventData> getBackwardRevisitModel(final GenMCExecutionModel oldModel, final List<EventData> eventsToDelete,
                                                                                     final Map<Thread, Event> boundEvents, final Event read, final Event write,
                                                                                     final Event lastRead) {
        final GenMCExecutionModel newModel = oldModel.newInstance(eventsToDelete, boundEvents);
        final EventData newWrite = newModel.initialize(read, write, boundEvents.get(read.getThread()), lastRead, oldModel.needsNewConsistencyCheck());
        return Map.entry(newModel, newWrite);
    }
}
