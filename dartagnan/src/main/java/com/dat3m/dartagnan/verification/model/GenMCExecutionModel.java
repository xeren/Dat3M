package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.program.specification.AssertInline;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.verification.VerificationTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.dat3m.dartagnan.program.event.Tag.READ;
import static com.dat3m.dartagnan.program.event.Tag.WRITE;
import static com.dat3m.dartagnan.verification.model.SimpleEventDomain.isThreadEndStore;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.CO;

/*
The ExecutionModel wraps a Model and extracts data from it in a more workable manner.
 */

//TODO: Add the capability to remove unnecessary init events from a model
// i.e. those that init some address which no read nor write accesses.
public class GenMCExecutionModel {

    private static final Logger logger = LogManager.getLogger(GenMCExecutionModel.class);

    // ============= Model specific  =============
    private SimpleEventDomain trace;
    private Event lastAdded = null;
    private boolean hasError = false;
    private boolean isConsistent = true;
    private boolean[] threadsAreJoined;

    private final EncodingContext encodingContext;
    private final GenMCTraceEncoder traceEncoder;
    private final ProverEnvironment prover;
    private final GenMCWMMSolver solver;
    private final BooleanFormula properties;
    private final Filter threadEventFilter;
    
    private final List<Thread> relevantThreads;
    private final Map<BooleanFormula, Integer> prohibitedCoherences;

    private final List<EventData> initEvents;
    private final Map<EventData, Set<EventData>> mayRfMap;

    private GenMCExecutionModel(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, ProverEnvironment prover, GenMCWMMSolver solver, BooleanFormula properties) {
        this.encodingContext = encodingContext;
        this.traceEncoder = traceEncoder;
        this.prover = prover;
        this.solver = solver;
        this.properties = properties;
        this.threadEventFilter = Filter.byEvents(getProgram().getThreadEvents());

        relevantThreads = new ArrayList<>(getProgram().getThreads());
        threadsAreJoined = new boolean[relevantThreads.size()];
        prohibitedCoherences = new HashMap<>();

        initEvents = new ArrayList<>(getProgram().getThreadEvents(Init.class).size() * 3);
        mayRfMap = new HashMap<>();
    }

    public static GenMCExecutionModel withContext(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, ProverEnvironment prover, BooleanFormula properties)
            throws InvalidConfigurationException {
        return new GenMCExecutionModel(encodingContext, traceEncoder, prover,
                GenMCWMMSolver.withContext(encodingContext.getTask().getMemoryModel(), encodingContext, encodingContext.getAnalysisContext()), properties);
    }

    public GenMCExecutionModel newInstance(int idToKeepCoBefore) throws InvalidConfigurationException {
        GenMCExecutionModel model = new GenMCExecutionModel(encodingContext, traceEncoder, prover, solver, properties);
        for (int i = 0; i < threadsAreJoined.length; ++i) {
            model.threadsAreJoined[i] = threadsAreJoined[i];
        }
        for (Map.Entry<BooleanFormula, Integer> prohibitedCoherence : prohibitedCoherences.entrySet()) {
            int currentMaxCoId = prohibitedCoherence.getValue();
            if (currentMaxCoId < idToKeepCoBefore) {
                model.prohibitedCoherences.put(prohibitedCoherence.getKey(), currentMaxCoId);
            }
        }
        return model;
    }

    //======================== Public data ===========================‚

    // General data
    public VerificationTask getTask() {
        return encodingContext.getTask();
    }

    public Program getProgram() {
        return encodingContext.getTask().getProgram();
    }

    // Model specific data
    public ExecutionModel getExecution() {
        return solver.getExecution();
    }
    public SimpleEventDomain getTrace() {
        return trace;
    }
    public boolean hasError() {
        return hasError;
    }
    public boolean isConsistent() {
        return isConsistent;
    }

    public Map<EventData, Set<EventData>> getMayRfMap() {
        return mayRfMap;
    }

    public Optional<EventData> getData(Event e) {
        return trace.getElements()
                .stream()
                .filter(event -> event.getEvent() == e)
                .findAny();
    }

    public boolean threadsAreJoined() {
        for (boolean threadIsJoined : threadsAreJoined) {
            if (!threadIsJoined) {
                return false;
            }
        }
        return true;
    }

    //========================== Initialization =========================

    public void initialize() throws InterruptedException, SolverException {
        List<Init> inits = getProgram().getThreadEvents(Init.class);
        initEvents.clear();
        for (Init init : inits) {
            Thread initThread = init.getThread();
            relevantThreads.remove(initThread);
            for (Event initThreadEvent : initThread.getEvents()) {
                initEvents.add(new EventData(initThreadEvent));
            }
        }
        trace = new SimpleEventDomain(initEvents);
        lastAdded = null;
        isConsistent = true;
        for (Thread thread : getProgram().getThreads()) {
            threadsAreJoined[thread.getId()] = !thread.getEntry().isSpawned();
        }
    }

    public void initialize(List<EventData> startTraceEvents, EventData revisitRead, EventData write) throws InterruptedException, SolverException {
        trace = new SimpleEventDomain(startTraceEvents);
        revisitRead.setReadFrom(write);
        for (EventData event : startTraceEvents) {
            EventData readFrom = event.getReadFrom();
            if (readFrom != null && !startTraceEvents.contains(readFrom)) {
                EventData newReadFrom = startTraceEvents.stream()
                        .filter(newEvent -> newEvent.getEvent() == readFrom.getEvent())
                        .findAny()
                        .get();
                event.setReadFrom(newReadFrom);
            }
        }
        Thread firstThread = trace.getElements()
                .stream()
                .map(EventData::getThread)
                .min(Comparator.comparingInt(relevantThreads::indexOf))
                .get();
        lastAdded = getLastAddedThreadEvent(firstThread);
        isConsistent = true;
        threadsAreJoined[write.getEvent().getThread().getId()] |= isThreadEndStore(write);
    }

    //========================== Public methods =========================

    public boolean checkConsistency(boolean ignoreLast) throws InterruptedException, SolverException {
        logger.debug("Checking consistency");
        EventData event = null;
        if (ignoreLast) {
            event = trace.removeLast();
        }
        Model model = calculateModel();
        if (model == null) {
            isConsistent = false;
            return false;
        }
        if (checkConsistency(model)) {
            if (ignoreLast) {
                trace.addEvent(event);
            }
            return true;
        } else {
            isConsistent = false;
            return false;
        }
    }

    public boolean checkProperties() throws InterruptedException, SolverException {
        if (properties == null) {
            logger.debug("No global properties to check");
            return true;
        }
        BooleanFormulaManager bmgr = encodingContext.getBooleanFormulaManager();
        return checkProperties(bmgr.and(properties, traceEncoder.getPropertyRelevantEncoding()));
    }

    public List<EventData> copyTraceEvents(Predicate<EventData> filter) {
        List<EventData> events = trace.getElements();
        EventMap eventMap = new EventMap();
        List<EventData> copiedEvents = new ArrayList<>(events.size());
        events.stream().filter(filter).forEach(event -> {
            EventData copiedEvent = eventMap.getOrCreate(event.getEvent());
            copiedEvent.setReadFrom(event.getReadFrom());
            copiedEvent.setWasExecuted(event.wasExecuted());
            copiedEvents.add(copiedEvent);
        });
        for (EventData event : copiedEvents) {
            if (event.getReadFrom() != null) {
                EventData newReadFrom = eventMap.get(event.getReadFrom().getEvent());
                if (newReadFrom != null) {
                    event.setReadFrom(eventMap.get(event.getReadFrom().getEvent()));
                }
            }
        }
        return copiedEvents;
    }

    public Set<EventData> getPorfPrefix(EventData event) {
        Set<EventData> porfPrefix = new HashSet<>(initEvents);
        porfPrefix.addAll(getPorfPrefixPreviousPo(event));
        porfPrefix.addAll(getPorfPrefixPreviousRf(event));
        porfPrefix.add(event);
        return porfPrefix;
    }

    public Set<Event> getEventsAfter(EventData event) {
        return trace.getElements()
                .stream()
                .filter(evt -> trace.getId(evt) > trace.getId(event))
                .map(EventData::getEvent)
                .collect(Collectors.toSet());
    }

    public EventData nextEvent() throws InterruptedException, SolverException {
        Filter eventFilter = Filter.difference(threadEventFilter, trace.eventFilter());
        Event startEvent;
        try {
            startEvent = trace.getObjectById(0).getEvent();
        } catch (NullPointerException e) {
            startEvent = getProgram().getThreads().get(0).getEntry();
        }
        int threadIndex = 0;
        boolean initialize = true;
        if (lastAdded != null) {
            startEvent = lastAdded;
            threadIndex = relevantThreads.indexOf(startEvent.getThread());
            initialize = false;
        }
        while (threadIndex < relevantThreads.size()) {
            Thread thread = relevantThreads.get(threadIndex);
            Event e = initialize ? getLastAddedThreadEvent(thread) : startEvent;
            initialize = true;
            while (e != null) {
                if (eventFilter.apply(e)) {
                    lastAdded = e;
                    return addEvent(e);
                }

                Optional<EventData> data = getData(e);
                if (e instanceof CondJump jump && data.isPresent() && data.get().wasExecuted()) {
                    e = jump.getLabel();
                    if (getData(e).isPresent()) {
                        // TODO: Handle loops
                    }
                } else {
                    e = e.getSuccessor();
                }

            }
            ++threadIndex;
        }
        return null;
    }

    //========================== Internal methods  =========================

    private EventData addEvent(Event e) throws InterruptedException, SolverException {
        EventData data = new EventData(e);

        data.setWasExecuted(true);
        if (data.isMemoryEvent()) {
            // ===== Memory Events =====
            Set<EventData> mayRfSet;
            if (trace.getLast().getEvent() instanceof ThreadStart start && start.isSpawned()) {
                mayRfSet = new HashSet<>();
                mayRfSet.add(getThreadStartStore(start));
            } else {
                mayRfSet = mayRfSet(data);
            }
            mayRfMap.put(data, mayRfSet);            
            if (data.isRead()) {
                try {
                    data.setReadFrom(mayRfSet.iterator().next());
                } catch (NoSuchElementException ex) {
                    isConsistent = false;
                    logger.error("Read " + e + " has nowhere to read from in trace " + trace);
                    throw ex;
                }
            } else if (!data.isWrite()) {
                //FIXME: Handle other kinds of memory events such as SRCU_SYNC.
                throw new UnsupportedOperationException("Unexpected memory event " + data.getEvent());
            }
            trace.addEvent(data);
        } else if (data.isJump()) {
            // ===== Jumps =====
            // We override the meaning of execution here. A jump is executed IFF its condition was true.
            CondJump jump = (CondJump) e;
            trace.addEvent(data);
            if (jump.getGuard() instanceof BoolLiteral bool) {
                data.setWasExecuted(bool.getValue());
            } else {
                data.setWasExecuted(calculateModel(encodingContext.jumpCondition(jump)) != null);
            }
        } else if (data.getEvent() instanceof Assert assertionEvent) {
            // ===== Assertions =====
            if (assertionEvent.getExpression() instanceof BoolLiteral bool) {
                trace.addEvent(data);
                boolean failure = !bool.getValue();
                if (failure && checkConsistency(false)) {
                    hasError = true;
                }
            } else {
                trace.addEvent(data);
                AssertInline assertion = new AssertInline(assertionEvent);
                hasError = !checkProperties(assertion.encode(encodingContext));
            }
        } else if (isThreadEndStore(trace.getLast())) {
            Event threadEndStore = trace.getLast().getEvent();
            boolean isRead = false;
            for (EventData event : trace.getElements()) {
                EventData readFrom = event.getReadFrom();
                if (readFrom != null && readFrom.getEvent() == threadEndStore) {
                    isRead = true;
                    break;
                }
            }
            if (!isRead) {
                threadsAreJoined[threadEndStore.getThread().getId()] = false;
            }
        }
        if (!trace.getElements().contains(data)) {
            trace.addEvent(data);
        }
        return data;
    }

    private Set<EventData> mayRfSet(EventData targetEvent) {
        if (!(targetEvent.getEvent() instanceof MemoryCoreEvent targetMemEvent)) {
            return Collections.emptySet();
        }
        String targetTag = targetEvent.isRead() ? WRITE : READ;
        Set<EventData> mayAliasSet = new HashSet<>();
        AliasAnalysis aliasAnalysis = encodingContext.getAnalysisContext().requires(AliasAnalysis.class);
        for (EventData event : trace.getElements()) {
            if (event.getEvent() instanceof MemoryCoreEvent memEvent && memEvent.hasTag(targetTag) && aliasAnalysis.mayAlias(targetMemEvent, memEvent)) {
                mayAliasSet.add(event);
            }
        }
        return mayAliasSet;
    }

    private EventData getThreadStartStore(ThreadStart thread) {
        return trace.getObjectById(trace.getId(getData(thread.getCreator()).get()) + 1);
    }

    private Set<EventData> getPorfPrefixPreviousPo(EventData event) {
        Optional<EventData> previousEvent = findPoPredecessor(event);
        if (previousEvent.isPresent()) {
            return getPorfPrefix(previousEvent.get());
        }
        return new HashSet<>();
    }

    private Optional<EventData> findPoPredecessor(EventData event) {
        EventData predecessor = null;
        Event currentPredecessor = event.getEvent();
        while (predecessor == null && (currentPredecessor = currentPredecessor.getPredecessor()) != null) {
            Event lambdaPredecessor = currentPredecessor;
            predecessor = trace.getElements()
                    .stream()
                    .filter(evt -> evt.getEvent() == lambdaPredecessor)
                    .findAny()
                    .orElse(null);
        }
        return Optional.ofNullable(predecessor);
    }

    private Set<EventData> getPorfPrefixPreviousRf(EventData event) {
        if (event.isRead()) {
            return getPorfPrefix(event.getReadFrom());
        }
        return Collections.emptySet();
    }

    private Event getLastAddedThreadEvent(Thread thread) {
        return trace.getElements()
                .stream()
                .filter(evt -> evt.getThread() == thread)
                .max(Comparator.comparingInt(trace::getId))
                .map(EventData::getEvent)
                .orElse(thread.getEntry());
    }

    private boolean checkProperties(BooleanFormula properties) throws InterruptedException, SolverException {
        logger.debug("Checking properties");
        List<BooleanFormula> propertiesList = new ArrayList<>();
        propertiesList.add(properties);
        Model model = calculateModel(propertiesList);
        if (model == null) {
            return true;
        }
        return !checkConsistency(model, propertiesList);
    }

    private Model calculateModel() throws InterruptedException, SolverException {
        return calculateModel(new ArrayList<>(1));
    }

    private Model calculateModel(BooleanFormula assumption) throws InterruptedException, SolverException {
        List<BooleanFormula> assumptions = new ArrayList<>(2);
        assumptions.add(assumption);
        return calculateModel(assumptions);
    }

    private Model calculateModel(Collection<BooleanFormula> assumptions) throws InterruptedException, SolverException {
        assumptions.addAll(prohibitedCoherences.keySet());
        assumptions.add(traceEncoder.encodeFullTrace(trace));
        Model model = null;
        if (!prover.isUnsatWithAssumptions(assumptions)) {
            model = prover.getModel();
        }
        return model;
    }

    private boolean checkConsistency(Model model) throws InterruptedException, SolverException {
        return checkConsistency(model, new ArrayList<>());
    }

    private boolean checkConsistency(Model model, List<BooleanFormula> assumptions) throws InterruptedException, SolverException {
        Filter eventFilter = Filter.intersection(Filter.byTag(Tag.VISIBLE), trace.eventFilter());
        while (model != null) {
            GenMCWMMSolver.Result result = solver.check(model, eventFilter);
            if (result.getStatus() == CAATSolver.Status.CONSISTENT) {
                return true;
            } else {
                logger.debug("Looking for consistent coherences");
                Map.Entry<BooleanFormula, Integer> probitedCoherence = prohibitCoherences();
                prohibitedCoherences.put(probitedCoherence.getKey(), probitedCoherence.getValue());
                model = calculateModel(assumptions);
            }
        }
        return false;
    }

    private Map.Entry<BooleanFormula, Integer> prohibitCoherences() {
        BooleanFormulaManager bmgr = encodingContext.getBooleanFormulaManager();
        EncodingContext.EdgeEncoder co = encodingContext.edge(getTask().getMemoryModel().getRelation(CO));
        Map<BigInteger, List<EventData>> coherences = solver.getExecution().getCoherenceMap();
        List<BooleanFormula> enc = new ArrayList<>();
        int maxId = -1;
        for (List<EventData> addressCoherence : coherences.values()) {
            Event pred = null;
            for (EventData eventData : addressCoherence) {
                Event event = eventData.getEvent();
                int eventId = trace.getId(getData(event));
                if (eventId > maxId) {
                    maxId = eventId;
                }
                if (pred != null) {
                    enc.add(co.encode(pred, event));
                }
                pred = event;
            }
        }
        return new AbstractMap.SimpleEntry<>(bmgr.not(bmgr.and(enc)), maxId);
    }
}
