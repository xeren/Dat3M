package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.configuration.OptionNames;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.processing.NonDetCollector;
import com.dat3m.dartagnan.expression.type.FunctionType;
import com.dat3m.dartagnan.expression.type.TypeFactory;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.event.functions.ValueFunctionCall;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.program.memory.Memory;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.program.processing.Intrinsics;
import com.dat3m.dartagnan.program.specification.AssertInline;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.utils.EventGraph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.dat3m.dartagnan.wmm.RelationNameRepository.CO;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.RF;
import static java.util.function.Predicate.not;

/*
The ExecutionModel wraps a Model and extracts data from it in a more workable manner.
 */

//TODO: Add the capability to remove unnecessary init events from a model
// i.e. those that init some address which no read nor write accesses.
@Options
public class GenMCExecutionModel {

    private static final Logger logger = LogManager.getLogger(GenMCExecutionModel.class);

    private static final ExpressionFactory expressions = ExpressionFactory.getInstance();
    private static final TypeFactory types = TypeFactory.getInstance();

    // =========================== Configurables ===========================

    @Option(name = OptionNames.INIT_DYNAMIC_ALLOCATIONS,
            description = "Creates init events for all dynamic allocations. Those init events zero out the memory.",
            secure = true)
    private boolean createInitsForDynamicAllocations = false;

    // ======================================================================

    private static int solverChecks = 0;
    private static int solverCheckTime = 0;
    private static int consistencyChecks = 0;
    private static int consistencyCheckTime = 0;

    // ============= Model specific  =============
    private SimpleEventDomain trace;
    private Event lastAdded = null;
    private boolean hasError = false;
    private boolean isConsistent = true;
    private boolean needsNewConsistencyCheck = true;
    private boolean lastNeedsNewConsistencyCheck = true;
    private Filter threadEventFilter;

    private final EncodingContext encodingContext;
    private final GenMCTraceEncoder traceEncoder;
    private final ProverEnvironment prover;
    private final GenMCWMMSolver solver;
    private final BooleanFormula properties;
    private final EventGraph mayRfGraph;
    
    private final List<Thread> relevantThreads;
    private final Map<BooleanFormula, Integer> prohibitedCoherences;
    private final List<Thread> threads;
    private final Memory memory;

    private final List<EventData> initEvents;
    private final Map<EventData, Set<EventData>> mayRfMap;

    private GenMCExecutionModel(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, ProverEnvironment prover, GenMCWMMSolver solver, BooleanFormula properties,
            Memory memory, boolean createInitsForDynamicAllocations) {
        this.createInitsForDynamicAllocations = createInitsForDynamicAllocations;
        
        this.encodingContext = encodingContext;
        this.traceEncoder = traceEncoder;
        this.prover = prover;
        this.solver = solver;
        this.properties = properties;
        this.threadEventFilter = Filter.byEvents(getProgram().getThreadEvents());
        this.mayRfGraph = encodingContext.getAnalysisContext().requires(RelationAnalysis.class).getKnowledge(getTask().getMemoryModel().getRelation(RF)).getMaySet();

        relevantThreads = new ArrayList<>(getProgram().getThreads());
        prohibitedCoherences = new HashMap<>();
        this.threads = new ArrayList<>();
        this.memory = memory;

        initEvents = new ArrayList<>();
        mayRfMap = new HashMap<>();
    }

    public static GenMCExecutionModel withContext(Configuration config, EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, ProverEnvironment prover,
            BooleanFormula properties) throws InvalidConfigurationException {
        VerificationTask task = encodingContext.getTask();
        GenMCWMMSolver solver = GenMCWMMSolver.withContext(task.getMemoryModel(), encodingContext, encodingContext.getAnalysisContext());
        Memory memory = task.getProgram().getMemory().copy();
        GenMCExecutionModel model = new GenMCExecutionModel(encodingContext, traceEncoder, prover, solver, properties, memory, false);
        config.inject(model);
        return model;
    }

    public GenMCExecutionModel newInstance(int idToKeepCoBefore) throws InvalidConfigurationException {
        GenMCExecutionModel model = new GenMCExecutionModel(encodingContext, traceEncoder, prover, solver, properties, memory.copy(), createInitsForDynamicAllocations);
        model.relevantThreads.removeIf(not(this.relevantThreads::contains));
        for (Map.Entry<BooleanFormula, Integer> prohibitedCoherence : prohibitedCoherences.entrySet()) {
            int currentMaxCoId = prohibitedCoherence.getValue();
            if (currentMaxCoId < idToKeepCoBefore) {
                model.prohibitedCoherences.put(prohibitedCoherence.getKey(), currentMaxCoId);
            }
        }
        return model;
    }

    //======================== Public data ===========================‚

    // Static data
    public static int getSolverChecks() {
        return solverChecks;
    }

    public static int getSolverCheckTime() {
        return solverCheckTime;
    }

    public static int getConsistencyChecks() {
        return consistencyChecks;
    }

    public static int getConsistencyCheckTime() {
        return consistencyCheckTime;
    }

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
        needsNewConsistencyCheck = false;
        lastNeedsNewConsistencyCheck = false;
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
                .filter(relevantThreads::contains)
                .min(Comparator.comparingInt(relevantThreads::indexOf))
                .get();
        lastAdded = getLastAddedThreadEvent(firstThread);
        isConsistent = true;
        needsNewConsistencyCheck = true;
        lastNeedsNewConsistencyCheck = false;
        
        for (MemoryObject memObj : memory.getObjects()) {
            if (!getData(memObj.getAllocationSite()).isPresent()) {
                free(memObj);
            }
        }
        for (EventData event : trace.getElements()) {
            if (event.getEvent() instanceof Alloc alloc && !alloc.isAllocated()) {
                allocate(alloc);
            }
        }
    }

    //========================== Public methods =========================

    public boolean checkConsistency(boolean ignoreLast) throws InterruptedException, SolverException {
        if ((!needsNewConsistencyCheck && !ignoreLast) || (!lastNeedsNewConsistencyCheck && ignoreLast)) {
            return isConsistent;
        }

        logger.debug("Checking consistency");
        EventData event = null;
        if (ignoreLast) {
            event = trace.removeLast();
        }
        long start = System.currentTimeMillis();
        ++consistencyChecks;
        Model model = calculateModel();
        if (model == null) {
            isConsistent = false;
            consistencyCheckTime += System.currentTimeMillis() - start;
            return false;
        }
        if (checkConsistency(model)) {
            if (ignoreLast) {
                lastNeedsNewConsistencyCheck = false;
                trace.addEvent(event);
            } else {
                needsNewConsistencyCheck = false;
            }
            isConsistent = true;
            consistencyCheckTime += System.currentTimeMillis() - start;
            return true;
        } else {
            isConsistent = false;
            consistencyCheckTime += System.currentTimeMillis() - start;
            return false;
        }
    }

    public boolean checkProperties() throws InterruptedException, SolverException {
        if (properties == null) {
            logger.debug("No global properties to check");
            return true;
        }
        BooleanFormulaManager bmgr = encodingContext.getBooleanFormulaManager();
        return !checkProperties(bmgr.and(properties, traceEncoder.getPropertyRelevantEncoding()));
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
        Filter traceFilter = trace.eventFilter();
        Filter eventFilter = Filter.difference(threadEventFilter, traceFilter);
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
                    Event predecessor = e.getPredecessor();
                    EventData addedEvent = addEvent(e);
                    if (addedEvent != null) {
                        return addedEvent;
                    }
                    eventFilter = Filter.difference(threadEventFilter, traceFilter);
                    e = predecessor.getSuccessor();
                    continue;
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
        NonDetCollector nonDetCollector = new NonDetCollector(trace);
        if (data.getEvent() instanceof RegWriter writer) {
            if (writer instanceof Local local) {
                data.setNonDet(local.getExpr().accept(nonDetCollector));
            }
        }
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
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
            if (data.isRead()) {
                try {
                    data.setReadFrom(mayRfSet.iterator().next());
                    if (mayRfSet.size() > 1) {
                        needsNewConsistencyCheck = true;
                    }
                } catch (NoSuchElementException ex) {
                    isConsistent = false;
                    logger.error("Read " + e + " has nowhere to read from in trace " + trace);
                    throw ex;
                }
            } else if (data.isWrite()) {
                Store write = (Store) data.getEvent();
                data.setNonDet(write.getMemValue().accept(nonDetCollector));
            } else {
                //FIXME: Handle other kinds of memory events such as SRCU_SYNC.
                throw new UnsupportedOperationException("Unexpected memory event " + data.getEvent());
            }
        } else if (data.getEvent() instanceof Alloc alloc) {
            allocate(alloc);
        } else if (data.getEvent() instanceof ValueFunctionCall call && call.isDirectCall()) {
            Intrinsics.Info intrinsic = call.getCalledFunction().getIntrinsicInfo();
            if (intrinsic == Intrinsics.Info.VERIFIER_NONDET) {
                Event predecessor = call.getPredecessor();
                Event successor = call.getSuccessor();
                Intrinsics.replace(call, intrinsic);
                List<Event> replacement = predecessor.getSuccessor().getSuccessors()
                    .stream()
                    .filter(successor.getPredecessor().getPredecessors()::contains)
                    .toList();
                encodingContext.replaceResultEvent(call, replacement);
                updateFilter();
                return null;
            }
        } else if (data.isJump()) {
            // ===== Jumps =====
            // We override the meaning of execution here. A jump is executed IFF its condition was true.
            CondJump jump = (CondJump) e;
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
            if (jump.getGuard() instanceof BoolLiteral bool) {
                data.setWasExecuted(bool.getValue());
            } else {
                if (jump.getGuard().accept(nonDetCollector)) {
                    if (checkConsistency(false)) {
                        // TODO: Handle non-determinism
                        logger.debug("Exploring non-deterministic jump");
                        data.setWasExecuted(calculateModel(encodingContext.jumpCondition(jump)) != null);
                    } else {
                        isConsistent = false;
                    }
                } else {
                    Model model;
                    if ((model = calculateModel()) != null) {
                        data.setWasExecuted(model.evaluate(encodingContext.jumpCondition(jump)));
                    } else {
                        isConsistent = false;
                    }
                }
            }
        } else if (data.getEvent() instanceof Assume assumptionEvent) {
            // ===== Assumptions =====
            if (!(trace.getLast().getEvent().getPredecessor() instanceof ThreadStart start && start.isSpawned())) {
                trace.addEvent(data);
                lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
                Expression assumption = assumptionEvent.getExpr();
                if (assumption instanceof BoolLiteral bool) {
                    isConsistent = bool.getValue();
                } else {
                    Model model;
                    if ((model = calculateModel()) != null) {
                        isConsistent = model.evaluate(encodingContext.encodeExpressionAsBooleanAt(assumption, assumptionEvent));
                    } else {
                        isConsistent = false;
                    }
                }
            }
        } else if (data.getEvent() instanceof Assert assertionEvent) {
            // ===== Assertions =====
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
            if (assertionEvent.getExpression() instanceof BoolLiteral bool) {
                boolean failure = !bool.getValue();
                if (failure && checkConsistency(false)) {
                    hasError = true;
                }
            } else {
                trace.addEvent(data);
                AssertInline assertion = new AssertInline(assertionEvent);
                hasError = !checkProperties(assertion.encode(encodingContext));
            }
        }
        if (!trace.getElements().contains(data)) {
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        }
        return data;
    }

    private Set<EventData> mayRfSet(EventData targetEvent) {
        Map<Event, Set<Event>> mayRfMap;
        if (targetEvent.isWrite()) {
            mayRfMap = mayRfGraph.getOutMap();
        } else {
            mayRfMap = mayRfGraph.getInMap();
        }
        Set<Event> mayRfSet = mayRfMap.get(targetEvent.getEvent());
        if (mayRfSet == null) {
            return Collections.emptySet();
        }
        return mayRfSet.stream()
                .map(this::getData)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
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

    private void updateFilter() {
        threadEventFilter = Filter.byEvents(getProgram().getThreadEvents());
    }

    private void allocate(Alloc alloc) {
        final MemoryObject allocatedObject = memory.allocate(alloc);
        alloc.setAllocatedObject(allocatedObject);

        if (alloc.doesZeroOutMemory() || createInitsForDynamicAllocations) {
            for (int i = 0; i < allocatedObject.size(); i++) {
                final Expression zero = expressions.makeZero(types.getByteType());
                allocatedObject.setInitialValue(i, zero);
            }
        }

        final FunctionType initThreadType = types.getFunctionType(types.getVoidType(), List.of());
        final List<String> paramNames = List.of();
        int nextThreadId = Stream.concat(threads.stream(), getProgram().getFunctions().stream())
            .mapToInt(Function::getId).max().getAsInt() + 1;
        for (int field : allocatedObject.getInitializedFields()) {
            // NOTE: We use different names to avoid symmetry detection treating all inits as symmetric.
            final String threadName = "Init_" + nextThreadId;
            final Thread thread = new Thread(threadName, initThreadType, paramNames, nextThreadId,
                    EventFactory.newThreadStart(null));
            thread.append(EventFactory.newInit(allocatedObject, field));
            thread.append(EventFactory.newLabel("END_OF_T" + thread.getId()));
            threads.add(thread);
            nextThreadId++;

            for (Event initThreadEvent : thread.getEvents()) {
                EventData event = new EventData(initThreadEvent);
                if (initThreadEvent instanceof Init) {
                    initEvents.add(event);
                }
                trace.addEvent(event);
            }
        }
    }

    private void free(MemoryObject memObj) {
        if (memObj.isDynamicallyAllocated()) {
            memory.deleteMemoryObject(memObj);
            memObj.getAllocationSite().setAllocatedObject(null);
            List<EventData> initEventsForObj = trace.getElements().stream()
                    .filter(e -> e.getEvent() instanceof Init init && init.getBase() == memObj)
                    .toList();
            for (EventData initEvent : initEventsForObj) {
                initEvents.remove(initEvent);
                Thread initThread = initEvent.getEvent().getThread();
                for (Event initThreadEvent : initThread.getEvents()) {
                    EventData initThreadData = getData(initThreadEvent).orElse(null);
                    if (initThreadData != null) {
                        trace.getElements().remove(initThreadData);
                    }
                }
                threads.remove(initThread);
            }
        }
    }

    private boolean checkProperties(BooleanFormula properties) throws InterruptedException, SolverException {
        logger.debug("Checking properties");
        List<BooleanFormula> propertiesList = new ArrayList<>();
        propertiesList.add(properties);
        Model model = calculateModel(propertiesList);
        if (model == null) {
            return false;
        }
        ++consistencyChecks;
        long start = System.currentTimeMillis();
        boolean execWithPropertiesFound = checkConsistency(model, propertiesList);
        consistencyCheckTime += System.currentTimeMillis() - start;
        return execWithPropertiesFound;
    }

    private Model calculateModel() throws InterruptedException, SolverException {
        return calculateModel(new ArrayList<>());
    }

    private Model calculateModel(BooleanFormula property) throws InterruptedException, SolverException {
        List<BooleanFormula> wrapper = new ArrayList<>();
        wrapper.add(property);
        return calculateModel(wrapper);
    }

    private Model calculateModel(Collection<BooleanFormula> assumptions) throws InterruptedException, SolverException {
        assumptions.addAll(prohibitedCoherences.keySet());
        assumptions.add(traceEncoder.encodeFullTrace(trace, memory));
        Model model = null;
        long start = System.currentTimeMillis();
        if (!prover.isUnsatWithAssumptions(assumptions)) {
            model = prover.getModel();
        }
        solverCheckTime += System.currentTimeMillis() - start;
        ++solverChecks;
        return model;
    }

    private boolean checkConsistency(Model model) throws InterruptedException, SolverException {
        return checkConsistency(model, new ArrayList<>());
    }

    private boolean checkConsistency(Model model, List<BooleanFormula> assumptions) throws InterruptedException, SolverException {
        Filter eventFilter = Filter.intersection(Filter.byTag(Tag.VISIBLE), trace.eventFilter());
        List<BooleanFormula> locallyProhibitedCoherences = new ArrayList<>();
        while (model != null) {
            GenMCWMMSolver.Result result = solver.check(model, eventFilter);
            if (result.getStatus() == CAATSolver.Status.CONSISTENT) {
                return true;
            } else {
                logger.debug("Looking for consistent coherences");
                Map.Entry<BooleanFormula, Integer> prohibitedCoherence = prohibitCoherences();
                BooleanFormula locallyProhibitedCoherence = prohibitedCoherence.getKey();
                locallyProhibitedCoherences.add(locallyProhibitedCoherence);
                prohibitedCoherences.put(locallyProhibitedCoherence, prohibitedCoherence.getValue());
                model = calculateModel(assumptions);
            }
        }
        locallyProhibitedCoherences.forEach(prohibitedCoherences::remove);
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
