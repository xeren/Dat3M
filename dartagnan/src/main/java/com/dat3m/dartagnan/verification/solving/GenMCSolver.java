package com.dat3m.dartagnan.verification.solving;

import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.encoding.WmmEncoder;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.processing.AssertionTransformer;
import com.dat3m.dartagnan.program.specification.AssertInline;
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
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.*;

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

    private int completeExecutions = 0;
    private int executions = 0;

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
        new AssertionTransformer().run(task.getProgram());
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
        if (task.getProgram().getSpecification() instanceof AssertInline) {
            properties = null;
        } else {
            properties = propertyEncoder.encodeProperties(task.getProperty());
        }
        final GenMCExecutionModel model = GenMCExecutionModel.withContext(context, traceEncoder, prover, properties);
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

        int failedExecutions = executions - completeExecutions;
        logger.info("Number of complete executions explored: " + completeExecutions);
        logger.info("Number of failed executions found: " + failedExecutions);
        if (completeExecutions + failedExecutions / 2 != 0) {
            logger.info("Time per execution estimation: " +
                    Utils.toTimeString((genmcTime + (completeExecutions + failedExecutions / 2) / 2) / (completeExecutions + failedExecutions / 2)));
        } else if (failedExecutions == 1) {
            logger.info("Time per execution estimation: " + Utils.toTimeString(genmcTime));
        }

        int solverChecks = GenMCExecutionModel.getSolverChecks();
        int solverCheckTime = GenMCExecutionModel.getSolverCheckTime();

        logger.info("Solver checks: " + solverChecks);
        logger.info("Total solver check time: " + Utils.toTimeString(solverCheckTime));
        logger.info("Average solver check time: " + Utils.toTimeString((solverCheckTime + solverChecks / 2) / solverChecks));
        
        int consistencyChecks = GenMCExecutionModel.getConsistencyChecks();
        int consistencyCheckTime = GenMCExecutionModel.getConsistencyCheckTime();

        logger.info("Consistency checks: " + consistencyChecks);
        logger.info("Total consistency check time: " + Utils.toTimeString(consistencyCheckTime));
        logger.info("Average consistency check time: " + Utils.toTimeString((consistencyCheckTime + consistencyChecks / 2) / consistencyChecks));

        logger.info("Total encoding time: " + Utils.toTimeString(traceEncoder.getEncodingTime()));

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
            Event revisitedRead = revisit.getRead();
            GenMCExecutionModel revisitModel = revisit.getModel(model);
            Event newWrite = revisitModel.getData(revisitedRead).get().getReadFrom().getEvent();
            logger.debug((revisit instanceof ForwardRevisit ? "Forward" : "Backward") + " revisiting execution for read " + revisitedRead.getGlobalId() + ": " + revisitedRead +
                    " and write " + newWrite.getGlobalId() + ": " + newWrite);

            Set<Event> nonRevisitableEvents = model.getEventsAfter(model.getData(revisitedRead).get());
            revisitableReads.removeAll(nonRevisitableEvents);
            backwardRevisits.remove(nonRevisitableEvents);
            if (!visit(revisitModel, revisitableReads, backwardRevisits, revisitsToDo)) {
                return false;
            }
            model = revisitModel;
            revisitsToDo.setReadOrder(model);
        }
        return true;
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
            if (event.isRead()) {
                Set<EventData> writesData = model.getMayRfMap().get(event);
                if (writesData.size() > 1 && !model.checkConsistency(true)) {
                    logger.debug("Aborting exploration: Inconsistent execution");
                    return true;
                }
                revisitableReads.add(event.getEvent());
                for (EventData writeData : writesData) {
                    if (writeData != event.getReadFrom()) {
                        logger.debug("Adding new forward revisit for load " + event.getEvent() + " and store " + writeData.getEvent());
                        revisitsToDo.add(new ForwardRevisit(event, writeData));
                    }
                }
            }
            if (event.isWrite()) {
                if (!calcBackwardRevisits(model, revisitableReads, backwardRevisits, revisitsToDo, event)) {
                    logger.debug("Aborting exploration: Inconsistent execution");
                    return true;
                }
            }
        }
        ++completeExecutions;
        return model.checkProperties();
    }

    private boolean calcBackwardRevisits(GenMCExecutionModel model, Set<Event> revisitableReads, Checklist backwardRevisits, Worklist revisitsToDo, EventData write)
            throws InterruptedException, SolverException {
        Set<EventData> writePrefix = model.getPorfPrefix(write);
        Set<EventData> relevantReads = model.getMayRfMap().get(write);
        relevantReads.removeIf(event -> writePrefix.contains(event) || !revisitableReads.contains(event.getEvent()));
        if (!relevantReads.isEmpty() && !model.checkConsistency(false)) {
            return false;
        }
        for (EventData readData : relevantReads) {
            BackwardRevisit newRevisit = new BackwardRevisit(model, readData, writePrefix);
            if (backwardRevisits.add(newRevisit)) {
                logger.debug("Adding new backward revisit for load" + readData.getEvent() + " and store " + write.getEvent());
                revisitsToDo.add(newRevisit);
            }
        }
        return true;
    }

    private static class Worklist {
        private final Map<Event, Set<Revisit>> revisitsToDo = new HashMap<>();
        private Comparator<Event> readsComparator;

        private void setReadOrder(GenMCExecutionModel model) {
            List<EventData> trace = model.getTrace().getElements();
            readsComparator = Comparator.comparingInt(event -> trace.indexOf(trace.stream()
                    .filter(data -> data.getEvent() == event)
                    .findAny()
                    .get()));
        }

        private void add(Revisit revisitGraph) {
            revisitsToDo.computeIfAbsent(revisitGraph.getRead(), evt -> new HashSet<>())
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
                    .filter(revisitsToDo::containsKey)
                    .filter(read -> !revisitsToDo.get(read).isEmpty())
                    .max(readsComparator);
        }
    }

    private static class Checklist {
        private Map<Event, Set<Map<Event, Event>>> backwardsRevisits = new HashMap<>();

        private boolean add(BackwardRevisit revisit) {
            return backwardsRevisits.computeIfAbsent(revisit.getRead(), event -> new HashSet<>())
                    .add(revisit.getRelevantReadsFromRelation());
        }

        private void remove(Set<Event> events) {
            for (Event event : events) {
                backwardsRevisits.remove(event);
            }
        }
    }

    private static abstract class Revisit {
        private final Event read;
        private GenMCExecutionModel model = null;

        protected Revisit(Event read) {
            this.read = read;
        }

        protected Event getRead() {
            return read;
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
            int oldReadId = oldModel.getTrace().getId(oldModel.getData(getRead()).get());
            List<EventData> traceEvents = oldModel.copyTraceEvents(data -> oldModel.getTrace().getId(data) <= oldReadId);
            EventData readData = null;
            EventData writeData = null;
            for (EventData event : traceEvents) {
                if (event.getEvent() == write) {
                    writeData = event;
                }
                if (event.getEvent() == getRead()) {
                    readData = event;
                }
            }
            assert readData != null && writeData != null;
            GenMCExecutionModel newModel = oldModel.newInstance(oldModel.getTrace().getId(writeData));
            newModel.initialize(traceEvents, readData, writeData);
            return newModel;
        }
    }

    private static class BackwardRevisit extends Revisit {
        private final List<EventData> eventsToKeep;

        private BackwardRevisit(GenMCExecutionModel oldModel, EventData read, Set<EventData> writePrefix) {
            super(read.getEvent());
            int readId = oldModel.getTrace().getId(read);
            eventsToKeep = oldModel.copyTraceEvents(event -> event == read || (oldModel.getTrace().getId(event) > readId && writePrefix.contains(event)));
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

        private Map<Event, Event> getRelevantReadsFromRelation() {
            Map<Event, Event> readsFromRelation = new HashMap<>();
            for (EventData relevantEventData : eventsToKeep) {
                EventData readFrom = relevantEventData.getReadFrom();
                if (readFrom != null) {
                    readsFromRelation.put(relevantEventData.getEvent(), readFrom.getEvent());
                }
            }
            readsFromRelation.put(getRead(), getNewWrite().getEvent());
            return readsFromRelation;
        }

        @Override
        protected GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException {
            int readId = oldModel.getTrace().getId(oldModel.getData(getRead()).get());
            List<EventData> newTraceEvents = oldModel.copyTraceEvents(event -> oldModel.getTrace().getId(event) < readId);
            newTraceEvents.addAll(eventsToKeep);
            GenMCExecutionModel newModel = oldModel.newInstance(readId);
            newModel.initialize(newTraceEvents, getNewRead(), getNewWrite());
            return newModel;
        }
    }
}
