package com.dat3m.dartagnan.verification.solving;

import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.encoding.WmmEncoder;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.solver.caat.domain.Domain;
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
        //instrumentPolaritySeparation(memoryModel);

        performStaticProgramAnalyses(task, analysisContext, config);
        performStaticWmmAnalyses(task, analysisContext, config);

        // ------------------------ Encoding ------------------------

        context = EncodingContext.of(task, analysisContext, ctx.getFormulaManager());
        final GenMCTraceEncoder traceEncoder = GenMCTraceEncoder.withContext(context);
        final PropertyEncoder propertyEncoder = PropertyEncoder.withContext(context);
        final WmmEncoder baselineEncoder = WmmEncoder.withContext(context);

        final GenMCExecutionModel model = GenMCExecutionModel.withContext(context, traceEncoder, prover);

        logger.info("Starting encoding using " + ctx.getVersion());
        prover.addConstraint(baselineEncoder.encodeFullMemoryModel());
        prover.addConstraint(propertyEncoder.encodeProperties(task.getProperty()));

        // ------------------------ Solving ------------------------
        prover.push();

        logger.info("GenMC algorithm started.");
        boolean result = genmc(model);

        if (logger.isInfoEnabled()) {
            final String message = !result ? "Specification violation found." : "Bounded specification proven.";
            logger.info(message);
        }

        // For Safety specs, we have SAT=FAIL, but for reachability specs, we have
        // SAT=PASS
        res = result ? Result.PASS : Result.FAIL;
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
        Set<EventData> revisitableReads = new HashSet<>();
        Checklist backwardsRevisits = new Checklist();
        Worklist revisitsToDo = new Worklist();
        if (!visit(model, revisitableReads, backwardsRevisits, revisitsToDo)) {
            return false;
        }
        Revisit revisit;
        while ((revisit = revisitsToDo.removeMax()) != null) {
            Event revisitedRead = revisit.getRead();
            GenMCExecutionModel revisitModel = revisit.getModel(model);
            EventData newRead = revisitModel.getData(revisitedRead).get();

            Domain<EventData> trace = model.getTrace();
            int revisitedReadId = trace.getId(model.getData(revisitedRead).get());
            Set<EventData> nonRevisitableEvents = trace.getElements()
                    .stream()
                    .filter(evt -> trace.getId(evt) > revisitedReadId)
                    .collect(Collectors.toSet());
            revisitableReads.removeAll(nonRevisitableEvents);
            backwardsRevisits.remove(nonRevisitableEvents);
            if (newRead.isWrite()) {
                calcBackwardRevisits(revisitModel, revisitableReads, backwardsRevisits, revisitsToDo, newRead);
            }
            if (!visit(revisitModel, revisitableReads, backwardsRevisits, revisitsToDo)) {
                return false;
            }
        }
        return true;
    }

    private boolean visit(GenMCExecutionModel model, Set<EventData> revisitableReads, Checklist backwardsRevisits, Worklist revisitsToDo) throws InterruptedException, SolverException {
        EventData event;
        while ((event = model.nextEvent()) != null) {
            if (event.isRead()) {
                Set<EventData> writesData = model.getMayAliasMap().get(event);
                revisitableReads.add(event);
                for (EventData writeData : writesData) {
                    if (writeData != event.getReadFrom()) {
                        revisitsToDo.add(new ForwardRevisit(event, writeData));
                    }
                }
            }
            if (event.isWrite()) {
                calcBackwardRevisits(model, revisitableReads, backwardsRevisits, revisitsToDo, event);
            }
        }
        model.check();
        return true;
    }

    private void calcBackwardRevisits(GenMCExecutionModel model, Set<EventData> revisitableReads, Checklist backwardsRevisits, Worklist revisitsToDo, 
            EventData write) {
        Set<EventData> writePrefix = model.getPorfPrefix(write);
        Set<EventData> readsForAddress = model.getMayAliasMap().get(write);
        Set<EventData> relevantReads = getRelevantReads(revisitableReads, readsForAddress, writePrefix);
        for (EventData readData : relevantReads) {
            BackwardRevisit newRevisit = new BackwardRevisit(model, readData, writePrefix);
            if (backwardsRevisits.add(readData, newRevisit)) {
                revisitsToDo.add(newRevisit);
            }
        }
    }

    private static <T> Set<T> getRelevantReads(Set<T> firstSet, Set<T> secondSet, Set<T> differenceSet) {
        Set<T> resultSet = new HashSet<>();
        for (T t : firstSet) {
            if (secondSet.contains(t) && !differenceSet.contains(t)) {
                resultSet.add(t);
            }
        }
        return resultSet;
    }

    private static class Worklist {
        private Map<Event, Set<Revisit>> revisitsToDo = new HashMap<>();
        private Stack<Event> readsToRevisit = new Stack<>();

        private void add(Revisit revisitGraph) {
            Event read = revisitGraph.getRead();
            if (!revisitsToDo.containsKey(read)) {
                readsToRevisit.push(read);
                revisitsToDo.put(read, new HashSet<>());
            }
            revisitsToDo.get(read).add(revisitGraph);
        }

        private Revisit removeMax() {
            if (readsToRevisit.isEmpty()) {
                return null;
            }
            Set<Revisit> revisitsForRead = revisitsToDo.get(readsToRevisit.peek());
            Revisit revisitGraph = revisitsForRead.stream()
                    .findAny()
                    .get();
            revisitsForRead.remove(revisitGraph);
            if (revisitsForRead.isEmpty()) {
                readsToRevisit.pop();
            }
            return revisitGraph;
        }
    }

    private static class Checklist {
        private Map<Event, Set<Map<Event, Event>>> backwardsRevisits = new HashMap<>();

        private boolean add(EventData read, BackwardRevisit revisit) {
            Set<Map<Event, Event>> savedRevisits = backwardsRevisits.computeIfAbsent(read.getEvent(), event -> new HashSet<>());
            Map<Event, Event> parsedRevisit = revisit.getRelevantReadsFromRelation();
            for (var savedRevisit : savedRevisits) {
                if (savedRevisit.equals(parsedRevisit)) {
                    return false;
                }
            }
            savedRevisits.add(parsedRevisit);
            return true;
        }

        private void remove(Set<EventData> events) {
            for (EventData event : events) {
                backwardsRevisits.remove(event.getEvent());
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
                if (event.getEvent() == getRead()) {
                    readData = event;
                }
                if (event.getEvent() == write) {
                    writeData = event;
                }
            }
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
                EventData event = copiedEvents.get(i);
                if (writePrefix.contains(event)) {
                    eventsToKeep.add(event);
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
            int readIndex = oldModel.getTrace().getId(oldModel.getData(getRead()));
            List<EventData> copiedEvents = oldModel.copyTraceEvents();
            for (int i = readIndex - 1; i >= 0; --i) {
                EventData event = copiedEvents.get(i);
                eventsToKeep.addFirst(event);
            }
            return newModel(oldModel, eventsToKeep, readData, eventsToKeep.getLast());
        }
    }
}
