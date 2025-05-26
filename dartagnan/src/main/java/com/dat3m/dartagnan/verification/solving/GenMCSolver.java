package com.dat3m.dartagnan.verification.solving;

import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemoryEvent;
import com.dat3m.dartagnan.program.processing.AssertionTransformer;
import com.dat3m.dartagnan.program.processing.ConsecutiveGotoSimplification;
import com.dat3m.dartagnan.program.processing.IdReassignment;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.Utils;
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

import static com.dat3m.dartagnan.configuration.OptionNames.DELAY_CONSISTENCY_CHECKS;
import static com.dat3m.dartagnan.configuration.OptionNames.SHOW_CONSISTENT_EXECUTIONS;
import static com.dat3m.dartagnan.configuration.OptionNames.USE_CAAT_REASONING;
import static com.dat3m.dartagnan.configuration.OptionNames.USE_SMT_SOLVER;
import static java.util.function.Predicate.not;

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
public class GenMCSolver extends ModelChecker {

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
            name=USE_CAAT_REASONING,
            description = "Use CAAT reasoning to learn during exploration",
            secure = true)
    boolean useCaatReasoning = true;
    
    @Option(
            name=USE_SMT_SOLVER,
            description = "Use SMT solver to resolve the data flow with GenMC",
            secure = true)
    boolean useSMTSolver = false;

    private int consistentExecutions = 0;
    private int completeExecutions = 0;
    private int failedExecutions = 0;
    private int blockedExecutions = 0;
    private int porfPrefixTime = 0;

    // ================================================================================================================
    // GenMC solver

    private GenMCSolver() {
    }

    //TODO: We do not yet use Witness information. The problem is that WitnessGraph.encode() generates
    // constraints on hb, which is not encoded in Refinement.
    //TODO (2): Add possibility for Refinement to handle CAT-properties (it ignores them for now).
    public static GenMCSolver run(SolverContext ctx, ProverEnvironment prover, VerificationTask task)
            throws InterruptedException, SolverException, InvalidConfigurationException {
        GenMCSolver solver = new GenMCSolver();
        task.getConfig().inject(solver);
        logger.info("{}: {}", SHOW_CONSISTENT_EXECUTIONS, solver.showConsistentExecutions);
        logger.info("{}: {}", USE_SMT_SOLVER, solver.useSMTSolver);
        solver.runInternal(ctx, prover, task);
        return solver;
    }

    private void runInternal(SolverContext ctx, ProverEnvironment prover, VerificationTask task)
            throws InterruptedException, SolverException, InvalidConfigurationException {
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
        ConsecutiveGotoSimplification.newInstance().run(task.getProgram());
        AssertionTransformer.newInstance().run(task.getProgram());
        IdReassignment.newInstance().run(task.getProgram());
        preprocessMemoryModel(task, config);

        performStaticProgramAnalyses(task, analysisContext, config);
        performStaticWmmAnalyses(task, analysisContext, config);

        removePorfAcyclicityAxiom(task);

        // ------------------------ Encoding ------------------------

        context = EncodingContext.of(task, analysisContext, ctx.getFormulaManager());
        logger.info("Used SMT solver: {}", ctx.getVersion());
        final GenMCTraceEncoder traceEncoder = GenMCTraceEncoder.withContext(context, prover);
        final PropertyEncoder propertyEncoder = PropertyEncoder.withContext(context);

        final BooleanFormula properties;
        if (task.getProgram().getFormat() != Program.SourceLanguage.LITMUS) {
            properties = null;
        } else {
            properties = propertyEncoder.encodeProperties(task.getProperty());
        }
        final GenMCExecutionModel model = GenMCExecutionModel.withContext(context, traceEncoder, prover, properties, useCaatReasoning, useSMTSolver);

        // ------------------------ Solving ------------------------

        logger.info("TruSt algorithm started.");
        long startTime = System.currentTimeMillis();
        boolean result = trust(model);
        long endTime = System.currentTimeMillis();
        long genmcTime = endTime - startTime;
        logger.info("TruSt took {}", Utils.toTimeString(genmcTime));

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
        logger.info("Time per execution estimation: {}", Utils.toTimeString(
                (genmcTime + (completeExecutions + (incompleteExecutions + 1) / 2) / 2) / (completeExecutions + (incompleteExecutions + 1) / 2)));

        int solverChecks = model.getSolverChecks();
        int solverCheckTime = model.getSolverCheckTime();

        logger.info("Solver checks: {}", solverChecks);
        if (solverChecks != 0) {
            logger.info("Total solver check time: {}", Utils.toTimeString(solverCheckTime));
            logger.info("Average solver check time: {}", Utils.toTimeString((solverCheckTime + solverChecks / 2) / solverChecks));
        }
        
        int consistencyChecks = GenMCExecutionModel.getConsistencyChecks();
        int consistencyCheckTime = GenMCExecutionModel.getConsistencyCheckTime();

        logger.info("Consistency checks: {}", consistencyChecks);
        if (consistencyChecks != 0) {
            logger.info("Total consistency check time: {}", Utils.toTimeString(consistencyCheckTime));
            logger.info("Average consistency check time: {}", Utils.toTimeString((consistencyCheckTime + consistencyChecks / 2) / consistencyChecks));
        }
        logger.info("Total reason evaluation time: {}", Utils.toTimeString(GenMCExecutionModel.getReasonEvaluationTime()));

        logger.info("Total encoding time: {}", Utils.toTimeString(traceEncoder.getEncodingTime()));
        logger.info("Total porf-prefix calculation time: {}", Utils.toTimeString(porfPrefixTime));
        logger.info("Total event filter time: {}", Utils.toTimeString(GenMCExecutionModel.getEventFilterTime()));
        logger.info("Total expression evaluation time: {}", Utils.toTimeString(model.getEvaluationTime()));
    }

    @Override
    public boolean hasModel() {
        return false;
    }

    private static void removeFlaggedAxiomsIfNotNeeded(VerificationTask task) {
        // We remove flagged axioms if we do not check for them.
        if (!task.getProperty().contains(Property.CAT_SPEC)) {
            List.copyOf(task.getMemoryModel().getAxioms()).stream()
                    .filter(Axiom::isFlagged)
                    .forEach(task.getMemoryModel()::removeConstraint);
        }
    }

    private static void removePorfAcyclicityAxiom(VerificationTask task) {
        // We remove po | rf acyclicity axioms as GenMC cannot create such executions.
        List.copyOf(task.getMemoryModel().getAxioms()).stream()
                .filter(GenMCSolver::isPorfAcyclicity)
                .forEach(task.getMemoryModel()::removeConstraint);
    }

    private static boolean isPorfAcyclicity(Axiom axiom) {
        if (axiom.isAcyclicity()) {
            String acyclicTerm = axiom.getRelation().getDefinition().getTerm();
            return "po | rf".equals(acyclicTerm) || "rf | po".equals(acyclicTerm);
        }
        return false;
    }

    private boolean trust(GenMCExecutionModel model) throws SolverException, InterruptedException, InvalidConfigurationException {
        model.initialize();
        return visit(model);
    }

    private boolean visit(GenMCExecutionModel model) throws SolverException, InterruptedException, InvalidConfigurationException {
        model.resetConsistencyStatus();
        EventData event = model.nextEvent();
        if (!model.isConsistent()) {
            ++failedExecutions;
            logger.debug("Aborting exploration: Inconsistent execution");
            return true;
        }
        if (model.hasError()) {
            return false;
        }
        if (event == null) {
            if (model.getTrace().isFinished(model.getProgram())) {
                ++completeExecutions;
                if (showConsistentExecutions && model.checkConsistency(false)) {
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
            if (!visitCos(model, event)) {
                return false;
            }
            Collection<EventData> writePrefix = getPorfPrefix(model, event);
            Collection<EventData> relevantReads = model.getMayRfSet(event);
            relevantReads.removeIf(writePrefix::contains);
            if (!delayConsistencyChecks && !relevantReads.isEmpty() && !event.isRMW() && !model.checkConsistency(true)) {
                ++failedExecutions;
                logger.debug("Aborting exploration: Inconsistent execution");
                return true;
            }
            for (EventData read : relevantReads) {
                int readId = model.getTrace().getId(read);
                GenMCEventDomain trace = model.getTrace();
                Map<Thread, Integer> prefixLimits = getPrefixLimits(model, writePrefix);
                List<EventData> eventsToDelete = trace.getElements().stream()
                        .filter(e -> readId < trace.getId(e) &&
                                !(writePrefix.contains(e) || (!(e.getEvent() instanceof MemoryEvent) && prefixLimits.get(e.getEvent().getThread()) > e.getEvent().getGlobalId())))
                        .toList();
                if (shouldRevisit(model, eventsToDelete, read, event, writePrefix)) {
                    logger.debug("Backward revisiting load {}: {} for store {}: {}", read.getEvent().getGlobalId(),
                            read.getEvent(), event.getEvent().getGlobalId(), event.getEvent());
                    GenMCExecutionModel backwardRevisitModel = getBackwardRevisitModel(model, eventsToDelete, read, event);
                    if (!visitCos(backwardRevisitModel, backwardRevisitModel.getTrace().getData(event.getEvent()).get())) {
                        return false;
                    }
                }
            }
            return true;
        }
        return visit(model);
    }

    private boolean visitJumps(GenMCExecutionModel model, EventData event) throws SolverException, InterruptedException, InvalidConfigurationException {
        if (model.needsJumpRevisit()) {
            model.setJump(event, event.wasExecuted());
            logger.debug("Visiting non-deterministic jump {}: {} with execution status {}",
                    event.getEvent().getGlobalId(), event.getEvent(), event.wasExecuted());
            if (!visit(model)) {
                return false;
            }
            model.rollback(event);
            model.setJump(event, !event.wasExecuted());
            logger.debug("Visiting non-deterministic jump {}: {} with execution status {}",
                    event.getEvent().getGlobalId(), event.getEvent(), event.wasExecuted());
            if (!model.isConsistent()) {
                logger.debug("Aborting exploration: Inconsistent execution");
                return true;
            }
        }
        return visit(model);
    }

    private boolean visitRfs(GenMCExecutionModel model, EventData read) throws SolverException, InterruptedException, InvalidConfigurationException {
        Collection<EventData> mayRfSet = model.getMayRfSet(read);
        if (mayRfSet.size() > 1 && !model.checkConsistency(true)) {
            ++failedExecutions;
            logger.debug("Aborting exploration: Inconsistent execution");
            return true;
        }
        boolean needsNewConsistencyCheck = model.needsNewConsistencyCheck();
        boolean lastNeedsNewConsistencyCheck = model.lastNeedsNewConsistencyCheck();
        for (EventData write : mayRfSet) {
            model.setRf(read, write);
            logger.debug("Forward revisiting load {}: {} for store {}: {}", read.getEvent().getGlobalId(),
                    read.getEvent(), write.getEvent().getGlobalId(), write.getEvent());
            if (!visit(model)) {
                return false;
            }
            model.rollback(read);
        }
        model.needsNewConsistencyCheck(needsNewConsistencyCheck);
        model.lastNeedsNewConsistencyCheck(lastNeedsNewConsistencyCheck);
        return true;
    }

    private boolean visitCos(GenMCExecutionModel model, EventData write) throws SolverException, InterruptedException, InvalidConfigurationException {
        Collection<EventData> mayCoSet = model.getMayCoSet(write);
        if (mayCoSet.isEmpty()) {
            model.getTrace().addAddressInit(write);
            return visit(model);
        }
        if (!delayConsistencyChecks && mayCoSet.size() > 1 && !model.checkConsistency(true)) {
            ++failedExecutions;
            logger.debug("Aborting exploration: Inconsistent execution");
            return true;
        }
        boolean needsNewConsistencyCheck = model.needsNewConsistencyCheck();
        boolean lastNeedsNewConsistencyCheck = model.lastNeedsNewConsistencyCheck();
        for (EventData otherWrite : mayCoSet) {
            model.setCo(otherWrite, write, mayCoSet);
            logger.debug("Visiting coherence ({}, {}): ({},  {})", otherWrite.getEvent().getGlobalId(),
                    write.getEvent().getGlobalId(), otherWrite.getEvent(), write.getEvent());
            if (!visit(model)) {
                return false;
            }
            model.rollbackCo(otherWrite, write);
        }
        model.needsNewConsistencyCheck(needsNewConsistencyCheck);
        model.lastNeedsNewConsistencyCheck(lastNeedsNewConsistencyCheck);
        return true;
    }

    private Collection<EventData> getPorfPrefix(GenMCExecutionModel model, EventData write) {
        long start = System.currentTimeMillis();
        Collection<EventData> porfPrefix = model.getPorfPrefix(write);
        porfPrefixTime += System.currentTimeMillis() - start;
        return porfPrefix;
    }

    private Map<Thread, Integer> getPrefixLimits(GenMCExecutionModel model, Collection<EventData> writePrefix) {
        Map<Thread, Integer> prefixLimits = new HashMap<>();
        for (Thread thread : model.getTrace().getThreadEventsMap().keySet()) {
            int limit = Integer.MIN_VALUE;
            for (EventData prefixData : writePrefix) {
                Event prefixEvent = prefixData.getEvent();
                int eventId;
                if (prefixEvent.getThread() == thread && prefixData.isMemoryEvent() && (eventId = prefixEvent.getGlobalId()) > limit) {
                    limit = eventId;
                }
            }
            prefixLimits.put(thread, limit);
        }
        return prefixLimits;
    }

    private boolean shouldRevisit(GenMCExecutionModel model, List<EventData> eventsToDelete, EventData read, EventData write, Collection<EventData> writePrefix) {
        for (EventData event : eventsToDelete) {
            if (!isMaximallyAdded(model, event, write, writePrefix)) {
                return false;
            }
        }
        return isMaximallyAdded(model, read, write, writePrefix);
    }

    private boolean isMaximallyAdded(GenMCExecutionModel model, EventData event, EventData write, Collection<EventData> writePrefix) {
        EventData maximalEvent;
        if (event.isWrite()) {
            for (EventData e : writePrefix) {
                if (e.getReadFrom() == event) {
                    return false;
                }
            }
            maximalEvent = event;
        } else if (event.isRead()) {
            maximalEvent = event.getReadFrom();
        } else {
            return true;
        }
        if (writePrefix.contains(maximalEvent.getCoSuccessor())) {
            return false;
        }
        GenMCEventDomain trace = model.getTrace();
        int eventId = trace.getId(event);
        List<EventData> previous = trace.getElements().subList(0, eventId + 1);
        for (EventData e : previous) {
            if (e.getReadFrom() == event) {
                return false;
            }
        }
        return (maximalEvent.getCoSuccessor() == null || !previous.contains(maximalEvent.getCoSuccessor())) &&
                (previous.contains(maximalEvent) || writePrefix.contains(maximalEvent));
    }

    private GenMCExecutionModel getBackwardRevisitModel(GenMCExecutionModel oldModel, List<EventData> eventsToDelete, EventData read, EventData write)
            throws InterruptedException, SolverException, InvalidConfigurationException {
        List<EventData> startTraceEvents = oldModel.copyTraceEvents(not(eventsToDelete::contains));
        GenMCExecutionModel newModel = oldModel.newInstance(startTraceEvents);
        EventData newRead = GenMCEventDomain.getData(startTraceEvents, read.getEvent()).get();
        EventData newWrite = GenMCEventDomain.getData(startTraceEvents, write.getEvent()).get();
        newModel.initialize(newRead, newWrite);
        return newModel;
    }
}
