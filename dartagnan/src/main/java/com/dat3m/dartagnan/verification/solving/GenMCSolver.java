package com.dat3m.dartagnan.verification.solving;

import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.encoding.WmmEncoder;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Init;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.EventMap;
import com.dat3m.dartagnan.verification.model.ExecutionModel;
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

    // ================================================================================================================
    // GenMC solver

    private GenMCTraceEncoder traceEncoder;

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
        traceEncoder = GenMCTraceEncoder.withContext(context);
        final PropertyEncoder propertyEncoder = PropertyEncoder.withContext(context);
        final WmmEncoder baselineEncoder = WmmEncoder.withContext(context);

        final GenMCWMMSolver solver = GenMCWMMSolver.withContext(memoryModel, context, analysisContext);

        logger.info("Starting encoding using " + ctx.getVersion());
        prover.addConstraint(baselineEncoder.encodeFullMemoryModel());
        prover.addConstraint(propertyEncoder.encodeProperties(task.getProperty()));

        // ------------------------ Solving ------------------------
        prover.push();

        logger.info("GenMC algorithm started.");
        boolean result = genmc(GenMCExecutionModel.withTask(task), solver);

        if (logger.isInfoEnabled()) {
            final String message = !result ? "Specification violation found." : "Bounded specification proven.";
            logger.info(message);
        }

        // For Safety specs, we have SAT=FAIL, but for reachability specs, we have
        // SAT=PASS
        res = result ? Result.UNKNOWN : Result.FAIL;
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

    private boolean genmc(GenMCExecutionModel model, GenMCWMMSolver solver) throws InvalidConfigurationException {
        model.initialize();
        Set<Event> revisitableReads = new HashSet<>();
        Checklist backwardsRevisits = new Checklist();
        Worklist revisitsToDo = new Worklist();
        if (!visit(model, revisitableReads, backwardsRevisits, revisitsToDo, solver, false)) {
            return false;
        }
        Revisit revisit;
        while ((revisit = revisitsToDo.removeMax()) != null) {
            Event revisitedRead = revisit.getRead();
            GenMCExecutionModel revisitModel = revisit.getModel(model);
            EventData newRead = revisitModel.getData(revisitedRead).get();

            Set<Event> nonRevisitableEvents = model.getEventsAfter(revisitedRead);
            revisitableReads.removeAll(nonRevisitableEvents);
            backwardsRevisits.remove(nonRevisitableEvents);
            if (newRead.isWrite()) {
                calcBackwardRevisits(revisitModel, revisitableReads, backwardsRevisits, revisitsToDo, newRead);
            }
            if (!visit(revisitModel, revisitableReads, backwardsRevisits, revisitsToDo, solver, true)) {
                return false;
            }
        }
        return true;
    }

    private Domain<EventData> initialTrace(Program program) {
        return new InitialDomain(program);
    }

    private boolean visit(GenMCExecutionModel model, Set<Event> revisitableReads, Checklist backwardsRevisits, Worklist revisitsToDo, GenMCWMMSolver consistencyChecker,
            boolean consistencyCheck) {
        Event newEvent;
        while ((!consistencyCheck || model.isConsistent(consistencyChecker)) && (newEvent = model.nextEvent()) != null) {
            consistencyCheck = true;
            EventData data = model.addEvent(newEvent);
            if (data == null) {
                return false;
            }
            if (data.isRead()) {
                Set<EventData> writesData = model.getAddressWritesMap().get(data.getAccessedAddress());
                revisitableReads.add(newEvent);
                for (EventData writeData : writesData) {
                    if (writeData != data.getReadFrom()) {
                        revisitsToDo.add(new Revisit(model, data, writeData, Collections.emptySet()));
                    }
                }
            }
            if (data.isWrite()) {
                calcBackwardRevisits(model, revisitableReads, backwardsRevisits, revisitsToDo, data);
            }
        }
        return true;
    }

    private void calcBackwardRevisits(GenMCExecutionModel model, Set<Event> revisitableReads, Checklist backwardsRevisits, Worklist revisitsToDo, 
            EventData writeData) {
        Set<EventData> writePrefix = model.getPorfPrefix(writeData);
        Set<EventData> readsForAddress = model.getAddressReadsMap().get(writeData.getAccessedAddress());
        Set<EventData> relevantReads = new HashSet<>(readsForAddress);
        relevantReads.removeAll(writePrefix);
        for (EventData readData : relevantReads) {
            Revisit newRevisit = new Revisit(model, readData, writeData, writePrefix);
            if (backwardsRevisits.add(readData, newRevisit)) {
                revisitsToDo.add(newRevisit);
            }
        }
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

        private boolean add(EventData read, Revisit revisit) {
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

        private void remove(Set<Event> events) {
            for (Event event : events) {
                backwardsRevisits.remove(event);
            }
        }
    }

    private static class Revisit {
        private final Event read;
        private final Event write;
        private final List<EventData> eventsToKeep;
        private GenMCExecutionModel model = null;

        private Revisit(GenMCExecutionModel oldModel, EventData read, EventData write, Set<EventData> writePrefix) {
            this.read = read.getEvent();
            this.write = write.getEvent();
            List<EventData> events = oldModel.getEventList();
            int readIndex = events.indexOf(read);
            eventsToKeep = new ArrayList<>();
            eventsToKeep.add(read);
            for (int i = readIndex + 1; i < events.size(); ++i) {
                EventData event = events.get(i);
                if (writePrefix.contains(event)) {
                    eventsToKeep.add(event);
                }
            }
        }

        private Event getRead() {
            return read;
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

        private GenMCExecutionModel getModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException {
            return model == null ? calculateModel(oldModel) : model;
        }

        private GenMCExecutionModel calculateModel(GenMCExecutionModel oldModel) throws InvalidConfigurationException {
            model = GenMCExecutionModel.withTask(oldModel.getTask());
            List<EventData> oldEvents = oldModel.getEventList();
            int readIndex = oldEvents.indexOf(oldModel.getData(read).get());
            for (int i = readIndex - 1; i >= 0; --i) {
                EventData event = oldEvents.get(i);
                eventsToKeep.addFirst(event);
            }
            model.initialize(eventsToKeep, read, write);
            return model;
        }
    }

    private static class InitialDomain implements Domain<EventData> {

        private List<EventData> initialEvents;

        private InitialDomain(Program program) {
            List<Init> inits = program.getThreadEvents(Init.class);
            EventMap map = new EventMap();
            initialEvents = new ArrayList<>(3 * inits.size());
            for (Init init : inits) {
                for (Event initThreadEvent : init.getThread().getEvents()) {
                    initialEvents.add(map.getOrCreate(initThreadEvent));
                }
            }
        }

        public int size() {
            return initialEvents.size();
        }

        public Collection<EventData> getElements() {
            return initialEvents;
        }

        public int getId(Object obj) {
            return initialEvents.indexOf(obj);
        }

        public EventData getObjectById(int id) {
            try {
                return initialEvents.get(id);
            } catch (IndexOutOfBoundsException e) {
                return null;
            }
        }
    }
}
