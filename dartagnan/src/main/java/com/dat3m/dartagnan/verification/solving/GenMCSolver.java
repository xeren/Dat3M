package com.dat3m.dartagnan.verification.solving;

import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.encoding.WmmEncoder;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.processing.AssertionTransformer;
import com.dat3m.dartagnan.program.processing.ConsecutiveGotoSimplification;
import com.dat3m.dartagnan.program.processing.IdReassignment;
import com.dat3m.dartagnan.solver.caat.domain.Domain;
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

import static com.dat3m.dartagnan.configuration.OptionNames.USE_SMT_SOLVER;

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
            name=USE_SMT_SOLVER,
            description = "Use SMT solver to resolve the data flow with GenMC",
            secure = true)
    boolean useSMTSolver = false;

    private int completeExecutions = 0;
    private int executions = 0;
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
        final GenMCTraceEncoder traceEncoder = GenMCTraceEncoder.withContext(context);
        final PropertyEncoder propertyEncoder = PropertyEncoder.withContext(context);
        final WmmEncoder baselineEncoder = WmmEncoder.withContext(context);

        final BooleanFormula properties;
        if (task.getProgram().getFormat() != Program.SourceLanguage.LITMUS) {
            properties = null;
        } else {
            properties = propertyEncoder.encodeProperties(task.getProperty());
        }
        final GenMCExecutionModel model = GenMCExecutionModel.withContext(context, traceEncoder, prover, properties, useSMTSolver);
        final Property.Type propertyType = Property.getCombinedType(task.getProperty(), task);

        logger.info("Starting encoding using " + ctx.getVersion());
        prover.addConstraint(baselineEncoder.encodeRelations());

        // ------------------------ Solving ------------------------
        prover.push();

        logger.info("GenMC algorithm started.");
        long startTime = System.currentTimeMillis();
        boolean result = genmc(model);
        long endTime = System.currentTimeMillis();
        long genmcTime = endTime - startTime;
        logger.info("GenMC took " + Utils.toTimeString(genmcTime));

        if (logger.isInfoEnabled()) {
            final String message = !result ? (propertyType == Property.Type.SAFETY ? "Specification violation found." : "Specification witness found.") :
                        (propertyType == Property.Type.SAFETY ? "Specification proven." : "Specification falsified.");
            logger.info(message);
        }
        res = result ? Result.PASS : Result.FAIL;

        // For Safety specs, we have SAT=FAIL, but for reachability specs, we have
        // SAT=PASS
        res = propertyType == Property.Type.SAFETY ? res : res.invert();
        logger.info("Verification finished with result " + res);

        int failedExecutions = executions - completeExecutions;
        logger.info("Number of complete executions explored: " + completeExecutions);
        logger.info("Number of failed executions found: " + failedExecutions);
        if (completeExecutions + failedExecutions / 2 != 0) {
            logger.info("Time per execution estimation: " +
                    Utils.toTimeString((genmcTime + (completeExecutions + failedExecutions / 2) / 2) / (completeExecutions + failedExecutions / 2)));
        } else if (failedExecutions == 1) {
            logger.info("Time per execution estimation: " + Utils.toTimeString(genmcTime));
        }

        int solverChecks = model.getSolverChecks();
        int solverCheckTime = model.getSolverCheckTime();

        logger.info("Solver checks: " + solverChecks);
        if (solverChecks != 0) {
            logger.info("Total solver check time: " + Utils.toTimeString(solverCheckTime));
            logger.info("Average solver check time: " + Utils.toTimeString((solverCheckTime + solverChecks / 2) / solverChecks));
        }
        
        int consistencyChecks = GenMCExecutionModel.getConsistencyChecks();
        int consistencyCheckTime = GenMCExecutionModel.getConsistencyCheckTime();

        logger.info("Consistency checks: " + consistencyChecks);
        if (consistencyChecks != 0) {
            logger.info("Total consistency check time: " + Utils.toTimeString(consistencyCheckTime));
            logger.info("Average consistency check time: " + Utils.toTimeString((consistencyCheckTime + consistencyChecks / 2) / consistencyChecks));
        }

        logger.info("Total encoding time: " + Utils.toTimeString(traceEncoder.getEncodingTime()));
        logger.info("Total porf-prefix calculation time: " + Utils.toTimeString(porfPrefixTime));
        logger.info("Total event filter time: " + Utils.toTimeString(GenMCExecutionModel.getEventFilterTime()));
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

    private boolean genmc(GenMCExecutionModel model) throws InvalidConfigurationException, SolverException, InterruptedException {
        model.initialize();
        Set<Event> revisitableReads = new HashSet<>();
        Checklist backwardRevisits = new Checklist();
        Worklist revisitsToDo = new Worklist();
        if (!visit(model, revisitableReads, backwardRevisits, revisitsToDo)) {
            return false;
        }
        revisitsToDo.setReadOrder(model);
        Revisit revisit;
        while ((revisit = revisitsToDo.removeMax()) != null) {
            Event revisitedEvent = revisit.getBacktrackingPoint();
            GenMCExecutionModel revisitModel = revisit.getModel(model);
            EventData revisitedData = revisitModel.getTrace().getData(revisitedEvent).get();
            boolean isRMWRevisit = revisitedData.isRMW();
            if (revisit instanceof ControlFlowRevisit cfRevisit) {
                isRMWRevisit |= cfRevisit.isForRMW();
                logger.debug("Doing control flow revisit for jump " + revisitedEvent.getGlobalId() + ": " + revisitedEvent + " with execution status " +
                        revisitModel.getTrace().getLast().wasExecuted());
            } else {
                Event newWrite = revisitedData.getReadFrom().getEvent();
                logger.debug((revisit instanceof ForwardRevisit ? "Forward" : "Backward") + " revisiting execution for read " + revisitedEvent.getGlobalId() + ": " + revisitedEvent +
                    " and write " + newWrite.getGlobalId() + ": " + newWrite);
            }

            Set<Event> nonRevisitableEvents = model.getEventsAfter(model.getTrace().getData(revisitedEvent).get());
            revisitableReads.removeAll(nonRevisitableEvents);
            backwardRevisits.remove(nonRevisitableEvents);
            
            if (isRMWRevisit) {
                EventData lastRMWEvent = visitRMW(revisitModel, revisitsToDo);
                if (lastRMWEvent.isWrite()) {
                    calcBackwardRevisits(revisitModel, revisitableReads, backwardRevisits, revisitsToDo, lastRMWEvent);
                }
            }

            if (!visit(revisitModel, revisitableReads, backwardRevisits, revisitsToDo)) {
                return false;
            }
            model = revisitModel;
            revisitsToDo.setReadOrder(model);
        }
        return true;
    }

    private EventData visitRMW(GenMCExecutionModel model, Worklist revisitsToDo) throws InterruptedException, SolverException {
        EventData rmwEvent;
        do {
            rmwEvent = model.nextEvent();
            if (rmwEvent.isJump()) {
                calcControlFlowRevisits(model, rmwEvent, revisitsToDo, true);
            }
        } while (!(rmwEvent.getEvent() instanceof Label || (rmwEvent.isWrite() && rmwEvent.isRMW())));
        return rmwEvent;
    }

    private boolean visit(GenMCExecutionModel model, Set<Event> revisitableReads, Checklist backwardRevisits, Worklist revisitsToDo) throws InterruptedException, SolverException {
        logger.debug("Exploring new execution");
        ++executions;
        EventData event;
        while ((event = model.nextEvent()) != null) {
            if (!model.isConsistent()) {
                logger.debug("Aborting exploration: Inconsistent execution");
                return true;
            }
            if (model.hasError()) return false;
            if (event.isJump()) {
                calcControlFlowRevisits(model, event, revisitsToDo, false);
            }
            if (event.isRMW()) {
                EventData rmwLoad = event;
                EventData lastRMWEvent = visitRMW(model, revisitsToDo);
                Set<EventData> writesData = model.getMayRfMap().get(rmwLoad);
                if (lastRMWEvent.isWrite()) {
                    EventData rmwStore = lastRMWEvent;
                    Collection<EventData> writePrefix = getPorfPrefix(model, rmwStore);
                    Set<EventData> revisitableReadsForWrite = getBackwardRevisitReads(model, writePrefix, revisitableReads, rmwStore);
                    if ((writesData.size() > 1 || !revisitableReadsForWrite.isEmpty()) && !model.checkConsistency(rmwLoad)) {
                        logger.debug("Aborting exploration: Inconsistent execution");
                        return true;
                    }
                    addForwardRevisits(revisitableReads, writesData, revisitsToDo, rmwLoad);
                    addBackwardRevisits(model, revisitableReadsForWrite, writePrefix, revisitsToDo, backwardRevisits, rmwStore);
                } else {
                    if (writesData.size() > 1 && !model.checkConsistency(rmwLoad)) {
                        logger.debug("Aborting exploration: Inconsistent execution");
                        return true;
                    }
                    addForwardRevisits(revisitableReads, writesData, revisitsToDo, rmwLoad);
                }
                continue;
            }
            if (event.isRead()) {
                Set<EventData> writesData = model.getMayRfMap().get(event);
                if (writesData.size() > 1 && !model.checkConsistency(event)) {
                    logger.debug("Aborting exploration: Inconsistent execution");
                    return true;
                }
                addForwardRevisits(revisitableReads, writesData, revisitsToDo, event);
            }
            if (event.isWrite()) {
                if (!calcBackwardRevisits(model, revisitableReads, backwardRevisits, revisitsToDo, event)) {
                    logger.debug("Aborting exploration: Inconsistent execution");
                    return true;
                }
            }
        }
        if (model.getTrace().isFinished(model.getProgram())) {
            ++completeExecutions;
        }
        return model.checkProperties();
    }

    private void calcControlFlowRevisits(GenMCExecutionModel model, EventData jump, Worklist revisitsToDo, boolean forRMW) {
        if (model.needsJumpRevisit()) {
            logger.debug("Adding new control flow revisit for jump " + jump.getEvent().getGlobalId() + ": " + jump.getEvent() + " with execution status " +
                    !jump.wasExecuted());
            revisitsToDo.add(new ControlFlowRevisit(jump, forRMW));
        }
    }

    private void addForwardRevisits(Set<Event> revisitableReads, Set<EventData> writesData, Worklist revisitsToDo, EventData read) {
        revisitableReads.add(read.getEvent());
        for (EventData writeData : writesData) {
            if (writeData != read.getReadFrom()) {
                logger.debug("Adding new forward revisit for load " + read.getEvent().getGlobalId() + ": " + read.getEvent() + " and store " +
                        writeData.getEvent().getGlobalId() + ": " + writeData.getEvent());
                revisitsToDo.add(new ForwardRevisit(read, writeData));
            }
        }
    }

    private boolean calcBackwardRevisits(GenMCExecutionModel model, Set<Event> revisitableReads, Checklist backwardRevisits, Worklist revisitsToDo, EventData write)
            throws InterruptedException, SolverException {
        Collection<EventData> writePrefix = getPorfPrefix(model, write);
        Set<EventData> relevantReads = getBackwardRevisitReads(model, writePrefix, revisitableReads, write);
        if (!relevantReads.isEmpty() && !write.isRMW() && !model.checkConsistency()) {
            return false;
        }
        addBackwardRevisits(model, relevantReads, writePrefix, revisitsToDo, backwardRevisits, write);
        return true;
    }

    private Collection<EventData> getPorfPrefix(GenMCExecutionModel model, EventData write) {
        long start = System.currentTimeMillis();
        Collection<EventData> porfPrefix = model.getPorfPrefix(write);
        porfPrefixTime += System.currentTimeMillis() - start;
        return porfPrefix;
    }

    private Set<EventData> getBackwardRevisitReads(GenMCExecutionModel model, Collection<EventData> writePrefix, Set<Event> revisitableReads, EventData write) {
        Set<EventData> relevantReads = model.getMayRfMap().get(write);
        relevantReads.removeIf(event -> writePrefix.contains(event) || !revisitableReads.contains(event.getEvent()));
        return relevantReads;
    }

    private void addBackwardRevisits(GenMCExecutionModel model, Set<EventData> readsToRevisit, Collection<EventData> writePrefix, Worklist revisitsToDo, Checklist backwardRevisits,
            EventData write) {
        for (EventData readData : readsToRevisit) {
            BackwardRevisit newRevisit = new BackwardRevisit(model, readData, writePrefix);
            if (backwardRevisits.add(newRevisit)) {
                logger.debug("Adding new backward revisit for load " + readData.getEvent().getGlobalId() + ": " + readData.getEvent() + " and store " +
                        write.getEvent().getGlobalId() + ": " + write.getEvent());
                revisitsToDo.add(newRevisit);
            }
        }
    }

    private static class Worklist {
        private final Map<Event, Set<Revisit>> revisitsToDo = new HashMap<>();
        private Comparator<Event> readsComparator;

        private void setReadOrder(GenMCExecutionModel model) {
            List<EventData> trace = model.getTrace().getElements();
            readsComparator = Comparator.comparingInt(event -> trace.indexOf(model.getTrace().getData(event).get()));
        }

        private void add(Revisit revisitGraph) {
            revisitsToDo.computeIfAbsent(revisitGraph.getBacktrackingPoint(), evt -> new HashSet<>())
                    .add(revisitGraph);
        }

        private Revisit removeMax() {
            Optional<Event> maximalRead = getMaximalRead();
            if (maximalRead.isEmpty()) {
                return null;
            }
            Set<Revisit> revisitsForRead = revisitsToDo.get(maximalRead.get());
            Revisit revisitGraph = revisitsForRead.stream()
                    .findAny()
                    .get();
            revisitsForRead.remove(revisitGraph);
            return revisitGraph;
        }

        private Optional<Event> getMaximalRead() {
            return revisitsToDo.keySet()
                    .stream()
                    .filter(read -> !revisitsToDo.get(read).isEmpty())
                    .max(readsComparator);
        }
    }

    private static class Checklist {
        private Map<Event, Set<Map<Event, Event>>> backwardsRevisits = new HashMap<>();

        private boolean add(BackwardRevisit revisit) {
            return backwardsRevisits.computeIfAbsent(revisit.getRead(), event -> new HashSet<>())
                    .add(revisit.getRelevantRevisitInfo());
        }

        private void remove(Set<Event> events) {
            for (Event event : events) {
                backwardsRevisits.remove(event);
            }
        }
    }

    private static abstract class Revisit {
        private final Event backtrackingPoint;
        private GenMCExecutionModel model = null;

        protected Revisit(Event backtrackingPoint) {
            this.backtrackingPoint = backtrackingPoint;
        }

        protected Event getBacktrackingPoint() {
            return backtrackingPoint;
        }

        protected GenMCExecutionModel getModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException {
            if (model == null) {
                model = calculateModel(oldModel);
            }
            return model;
        }

        protected abstract GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException;
    }

    private static class ForwardRevisit extends Revisit {
        private final Event write;

        private ForwardRevisit(EventData read, EventData write) {
            super(read.getEvent());
            this.write = write.getEvent();
        }

        @Override
        protected GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException {
            int backtrackingId = oldModel.getTrace().getId(oldModel.getTrace().getData(getBacktrackingPoint()).get());
            List<EventData> traceEvents = oldModel.copyTraceEvents(data -> oldModel.getTrace().getId(data) <= backtrackingId);
            EventData readData = traceEvents.get(traceEvents.size() - 1);
            EventData writeData = traceEvents.stream()
                    .filter(event -> event.getEvent() == write)
                    .findAny()
                    .get();
            GenMCExecutionModel newModel = oldModel.newInstance(oldModel.getTrace().getId(writeData));
            newModel.initialize(traceEvents, readData, writeData, new HashMap<>(), Collections.emptyList());
            return newModel;
        }
    }

    private static class BackwardRevisit extends Revisit {
        private final List<EventData> eventsToKeep;
        private final Map<Event, Event> nonDetJumpStatus;

        private BackwardRevisit(GenMCExecutionModel oldModel, EventData read, Collection<EventData> writePrefix) {
            super(read.getEvent());
            Domain<EventData> oldTrace = oldModel.getTrace();
            int readId = oldTrace.getId(read);
            eventsToKeep = oldModel.copyTraceEvents(event -> event == read || (oldTrace.getId(event) > readId && writePrefix.contains(event)));
            nonDetJumpStatus = new HashMap<>();
            for (EventData relevantEventData : eventsToKeep) {
                Event event = relevantEventData.getEvent();
                if (event instanceof CondJump jump && relevantEventData.isNonDet()) {
                    Event successor = relevantEventData.wasExecuted() ? jump.getLabel() : jump.getSuccessor();
                    nonDetJumpStatus.put(event, successor);
                }
            }
        }

        private Event getRead() {
            return getBacktrackingPoint();
        }

        private EventData getNewRead() {
            return eventsToKeep.stream()
                    .filter(event -> event.getEvent() == getRead())
                    .findAny()
                    .get();
        }

        private EventData getNewWrite() {
            return eventsToKeep.get(eventsToKeep.size() - 1);
        }

        private Map<Event, Event> getRelevantRevisitInfo() {
            Map<Event, Event> revisitInfo = new HashMap<>();
            for (EventData relevantEventData : eventsToKeep) {
                EventData readFrom = relevantEventData.getReadFrom();
                if (readFrom != null) {
                    revisitInfo.put(relevantEventData.getEvent(), readFrom.getEvent());
                }
            }
            revisitInfo.put(getRead(), getNewWrite().getEvent());
            revisitInfo.putAll(nonDetJumpStatus);
            return revisitInfo;
        }

        @Override
        protected GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException {
            Domain<EventData> oldTrace = oldModel.getTrace();
            int backtrackingId = oldTrace.getId(oldModel.getTrace().getData(getBacktrackingPoint()).get());
            List<EventData> newTraceEvents = oldModel.copyTraceEvents(event -> oldTrace.getId(event) < backtrackingId);
            newTraceEvents.addAll(eventsToKeep);
            GenMCExecutionModel newModel = oldModel.newInstance(backtrackingId);
            newModel.initialize(newTraceEvents, getNewRead(), getNewWrite(), nonDetJumpStatus, eventsToKeep);
            return newModel;
        }
    }

    private static class ControlFlowRevisit extends Revisit {
        private final boolean willJumpBeExecuted;
        private final boolean forRMW;

        private ControlFlowRevisit(EventData jump, boolean forRMW) {
            super(jump.getEvent());
            this.willJumpBeExecuted = !jump.wasExecuted();
            this.forRMW = forRMW;
        }

        private boolean isForRMW() {
            return forRMW;
        }

        @Override
        protected GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException {
            int backtrackId = oldModel.getTrace().getId(oldModel.getTrace().getData(getBacktrackingPoint()).get());
            List<EventData> newTraceEvents = oldModel.copyTraceEvents(event -> oldModel.getTrace().getId(event) <= backtrackId);
            EventData jumpData = newTraceEvents.get(newTraceEvents.size() - 1);
            GenMCExecutionModel newModel = oldModel.newInstance(backtrackId);
            newModel.initialize(newTraceEvents, jumpData, willJumpBeExecuted);
            return newModel;
        }
    }
}
