package com.dat3m.dartagnan.verification.solving;

import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.encoding.WmmEncoder;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.GenMCExecutionModel;
import com.dat3m.dartagnan.verification.model.SimpleEventDomain;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.*;

import java.util.*;
import java.util.stream.Collectors;


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
        preprocessMemoryModel(task, config);

        performStaticProgramAnalyses(task, analysisContext, config);
        performStaticWmmAnalyses(task, analysisContext, config);

        // ------------------------ Encoding ------------------------

        context = EncodingContext.of(task, analysisContext, ctx.getFormulaManager());
        final GenMCTraceEncoder traceEncoder = GenMCTraceEncoder.withContext(context);
        final PropertyEncoder propertyEncoder = PropertyEncoder.withContext(context);
        final WmmEncoder baselineEncoder = WmmEncoder.withContext(context);

        final BooleanFormula properties = propertyEncoder.encodeProperties(task.getProperty());
        final GenMCExecutionModel model = GenMCExecutionModel.withContext(context, traceEncoder, prover, properties);
        final Property.Type propertyType = Property.getCombinedType(task.getProperty(), task);

        logger.info("Starting encoding using " + ctx.getVersion());
        prover.addConstraint(baselineEncoder.encodeFullMemoryModel());

        // ------------------------ Solving ------------------------
        prover.push();

        logger.info("GenMC algorithm started.");
        boolean result = genmc(model);

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

    private boolean genmc(GenMCExecutionModel model) throws InvalidConfigurationException, SolverException, InterruptedException {
        model.initialize();
        Set<Event> revisitableReads = new HashSet<>();
        Checklist backwardsRevisits = new Checklist();
        Worklist revisitsToDo = new Worklist();
        if (!visit(model, revisitableReads, backwardsRevisits, revisitsToDo)) {
            return false;
        }
        revisitsToDo.setReadOrder(model);
        Revisit revisit;
        while ((revisit = revisitsToDo.removeMax()) != null) {
            Event revisitedRead = revisit.getRead();
            GenMCExecutionModel revisitModel = revisit.getModel(model);
            EventData newRead = revisitModel.getData(revisitedRead).get();
            logger.info((revisit instanceof ForwardRevisit ? "Forward" : "Backward") + " revisiting execution for read " + revisitedRead + " and write "
                    + newRead.getReadFrom().getEvent());

            Set<Event> nonRevisitableEvents = model.getEventsAfter(model.getData(revisitedRead).get());
            revisitableReads.removeAll(nonRevisitableEvents);
            backwardsRevisits.remove(nonRevisitableEvents);
            if (!visit(revisitModel, revisitableReads, backwardsRevisits, revisitsToDo)) {
                return false;
            }
            model = revisitModel;
            revisitsToDo.setReadOrder(model);
        }
        return true;
    }

    private boolean visit(GenMCExecutionModel model, Set<Event> revisitableReads, Checklist backwardsRevisits, Worklist revisitsToDo) throws InterruptedException, SolverException {
        logger.info("Exploring new execution");
        EventData event;
        while ((event = model.nextEvent()) != null) {
            if (!model.isConsistent()) {
                logger.info("Aborting exploration: Inconsistent execution");
                return true;
            }
            if (model.hasError()) return false;
            if (event.isRead()) {
                Set<EventData> writesData = model.getMayAliasMap()
                        .get(event)
                        .stream()
                        .filter(EventData::isWrite)
                        .collect(Collectors.toSet());
                if (writesData.size() > 1 && !model.checkConsistency(true)) {
                    logger.info("Aborting exploration: Inconsistent execution");
                    return true;
                }
                revisitableReads.add(event.getEvent());
                for (EventData writeData : writesData) {
                    if (writeData != event.getReadFrom()) {
                        logger.info("Adding new forward revisit for " + event.getEvent());
                        revisitsToDo.add(new ForwardRevisit(event, writeData));
                    }
                }
            }
            if (event.isWrite()) {
                if (!calcBackwardRevisits(model, revisitableReads, backwardsRevisits, revisitsToDo, event)) {
                    logger.info("Aborting exploration: Inconsistent execution");
                    return true;
                }
            }
        }
        return model.checkProperties();
    }

    private boolean calcBackwardRevisits(GenMCExecutionModel model, Set<Event> revisitableReads, Checklist backwardsRevisits, Worklist revisitsToDo, EventData write)
            throws InterruptedException, SolverException {
        Set<EventData> writePrefix = model.getPorfPrefix(write);
        Set<EventData> readsForAddress = model.getMayAliasMap().get(write);
        Set<EventData> relevantReads = getRelevantReads(revisitableReads, readsForAddress, writePrefix);
        if (!relevantReads.isEmpty() && !model.checkConsistency(false)) {
            return false;
        }
        for (EventData readData : relevantReads) {
            logger.info("Adding new backward revisit with " + write.getEvent());
            BackwardRevisit newRevisit = new BackwardRevisit(model, readData, writePrefix);
            if (backwardsRevisits.add(readData, newRevisit)) {
                revisitsToDo.add(newRevisit);
            }
        }
        return true;
    }

    private static Set<EventData> getRelevantReads(Set<Event> revisitableReads, Set<EventData> readsForAddress, Set<EventData> writePrefix) {
        Set<EventData> resultSet = new HashSet<>();
        Set<Event> writePrefixEvents = writePrefix.stream()
                .map(EventData::getEvent)
                .collect(Collectors.toSet());
        for (EventData data : readsForAddress) {
            Event event = data.getEvent();
            if (revisitableReads.contains(event) && !writePrefixEvents.contains(event)) {
                resultSet.add(data);
            }
        }
        return resultSet;
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
            Event read = revisitGraph.getRead();
            if (!revisitsToDo.containsKey(read)) {
                revisitsToDo.put(read, new HashSet<>());
            }
            revisitsToDo.get(read).add(revisitGraph);
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
        private Map<Event, List<Map<Event, Event>>> backwardsRevisits = new HashMap<>();

        private boolean add(EventData read, BackwardRevisit revisit) {
            List<Map<Event, Event>> savedRevisits = backwardsRevisits.computeIfAbsent(read.getEvent(), event -> new ArrayList<>());
            Map<Event, Event> parsedRevisit = revisit.getRelevantReadsFromRelation();
            for (var savedRevisit : savedRevisits) {
                if (savedRevisit.equals(parsedRevisit)) {
                    return false;
                }
            }
            savedRevisits.add(parsedRevisit);
            return true;
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
            return model == null ? calculateModel(oldModel) : model;
        }

        protected abstract GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException;

        protected static GenMCExecutionModel newModel(GenMCExecutionModel oldModel, List<EventData> traceEvents, EventData read, EventData write)
                throws InvalidConfigurationException, InterruptedException, SolverException {
            GenMCExecutionModel newModel = oldModel.newInstance();
            newModel.initialize(traceEvents, read, write);
            return newModel;
        }
    }

    private static class ForwardRevisit extends Revisit {
        private final Event write;

        private ForwardRevisit(EventData read, EventData write) {
            super(read.getEvent());
            this.write = write.getEvent();
        }

        @Override
        protected GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException {
            List<EventData> traceEvents = oldModel.copyTraceEvents();
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
            EventData readDataRef = readData;
            traceEvents.removeIf(data -> traceEvents.indexOf(data) > traceEvents.indexOf(readDataRef));
            return newModel(oldModel, traceEvents, readData, writeData);
        }
    }

    private static class BackwardRevisit extends Revisit {
        private final List<EventData> eventsToKeep;

        private BackwardRevisit(GenMCExecutionModel oldModel, EventData read, Set<EventData> writePrefix) {
            super(read.getEvent());
            SimpleEventDomain oldTrace = oldModel.getTrace();
            int readIndex = oldTrace.getId(read);
            List<EventData> copiedEvents = oldModel.copyTraceEvents();
            eventsToKeep = new ArrayList<>();
            eventsToKeep.add(copiedEvents.get(readIndex));
            for (int i = readIndex + 1; i < copiedEvents.size(); ++i) {
                if (writePrefix.contains(oldTrace.getObjectById(i))) {
                    eventsToKeep.add(copiedEvents.get(i));
                }
            }
        }

        private Map<Event, Event> getRelevantReadsFromRelation() {
            Map<Event, Event> readsFromRelation = new HashMap<>();
            for (EventData relevantEventData : eventsToKeep) {
                EventData readFrom = relevantEventData.getReadFrom();
                if (readFrom != null) {
                    readsFromRelation.put(relevantEventData.getEvent(), readFrom.getEvent());
                }
            }
            return readsFromRelation;
        }

        @Override
        protected GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException, InterruptedException, SolverException {
            EventData readData = eventsToKeep.getFirst();
            int readIndex = oldModel.getTrace().getId(oldModel.getData(getRead()).get());
            List<EventData> copiedEvents = oldModel.copyTraceEvents();
            for (int i = readIndex - 1; i >= 0; --i) {
                EventData event = copiedEvents.get(i);
                eventsToKeep.addFirst(event);
            }
            return newModel(oldModel, eventsToKeep, readData, eventsToKeep.getLast());
        }
    }
}
