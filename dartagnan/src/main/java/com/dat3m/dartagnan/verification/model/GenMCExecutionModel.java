package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.processing.BoolExpressionEvaluator;
import com.dat3m.dartagnan.expression.processing.FloatExpressionEvaluator;
import com.dat3m.dartagnan.expression.processing.IntExpressionEvaluator;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.threading.ThreadArgument;
import com.dat3m.dartagnan.program.event.lang.svcomp.BeginAtomic;
import com.dat3m.dartagnan.program.event.lang.svcomp.EndAtomic;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Wmm;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.dat3m.dartagnan.program.event.Tag.READ;
import static com.dat3m.dartagnan.solver.caat.CAATSolver.Status.CONSISTENT;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.CO;
import static com.google.common.base.Preconditions.checkNotNull;

/*
The ExecutionModel wraps a Model and extracts data from it in a more workable manner.
 */

//TODO: Add the capability to remove unnecessary init events from a model
// i.e. those that init some address which no read nor write accesses.
public class GenMCExecutionModel {

    private static final Logger logger = LogManager.getLogger(ExecutionModel.class);

    private final VerificationTask task;

    // ============= Model specific  =============
    private final EventMap eventMap;
    private final ArrayList<EventData> eventList;
    private final ArrayList<Thread> threadList;
    private final Map<Thread, List<EventData>> threadEventsMap;
    private final Map<Thread, List<List<EventData>>> atomicBlocksMap;
    private final Map<EventData, EventData> readWriteMap;
    private final Map<EventData, Set<EventData>> writeReadsMap;
    private final Map<String, Set<EventData>> fenceMap;
    private final Map<BigInteger, Set<EventData>> addressReadsMap;
    private final Map<BigInteger, Set<EventData>> addressWritesMap; // This ALSO contains the init writes
    private final Map<BigInteger, EventData> addressInitMap;
    //Note, we could merge the three above maps into a single one that holds writes, reads and init writes.

    private final Map<BigInteger, List<EventData>> coherenceMap;

    // The following are a read-only views which get passed to the outside
    private List<EventData> eventListView;
    private List<Thread> threadListView;
    private Map<Thread, List<EventData>> threadEventsMapView;
    private Map<Thread, List<List<EventData>>> atomicBlocksMapView;
    private Map<EventData, EventData> readWriteMapView;
    private Map<EventData, Set<EventData>> writeReadsMapView;
    private Map<String, Set<EventData>> fenceMapView;
    private Map<BigInteger, Set<EventData>> addressReadsMapView;
    private Map<BigInteger, Set<EventData>> addressWritesMapView;
    private Map<BigInteger, EventData> addressInitMapView;

    private Map<BigInteger, List<EventData>> coherenceMapView;

    private GenMCExecutionModel(VerificationTask task) {
        this.task = checkNotNull(task);

        eventList = new ArrayList<>(100);
        threadList = new ArrayList<>(getProgram().getThreads().size());
        threadEventsMap = new HashMap<>(getProgram().getThreads().size() * 4/3, 0.75f);
        atomicBlocksMap = new HashMap<>();
        readWriteMap = new HashMap<>();
        writeReadsMap = new HashMap<>();
        fenceMap = new HashMap<>();
        addressReadsMap = new HashMap<>();
        addressWritesMap = new HashMap<>();
        addressInitMap = new HashMap<>();
        eventMap = new EventMap();
        coherenceMap = new HashMap<>();

        createViews();
    }

    public static GenMCExecutionModel withTask(VerificationTask task) throws InvalidConfigurationException {
        return new GenMCExecutionModel(task);
    }

    private void createViews() {
        eventListView = Collections.unmodifiableList(eventList);
        threadListView = Collections.unmodifiableList(threadList);
        threadEventsMapView = Collections.unmodifiableMap(threadEventsMap);
        atomicBlocksMapView = Collections.unmodifiableMap(atomicBlocksMap);
        readWriteMapView = Collections.unmodifiableMap(readWriteMap);
        writeReadsMapView = Collections.unmodifiableMap(writeReadsMap);
        fenceMapView = Collections.unmodifiableMap(fenceMap);
        addressReadsMapView = Collections.unmodifiableMap(addressReadsMap);
        addressWritesMapView = Collections.unmodifiableMap(addressWritesMap);
        addressInitMapView = Collections.unmodifiableMap(addressInitMap);
        coherenceMapView = Collections.unmodifiableMap(coherenceMap);
    }

    //======================== Public data ===========================‚

    // General data
    public VerificationTask getTask() {
        return task;
    }
    
    public Wmm getMemoryModel() {
        return getTask().getMemoryModel();
    }

    public Program getProgram() {
        return getTask().getProgram();
    }

    public List<EventData> getEventList() {
        return eventListView;
    }

    public List<Thread> getThreads() {
        return threadListView;
    }
    
    public Map<Thread, List<EventData>> getThreadEventsMap() {
        return threadEventsMapView;
    }
    public Map<Thread, List<List<EventData>>> getAtomicBlocksMap() { return atomicBlocksMapView; }
    public Map<EventData, EventData> getReadWriteMap() {
        return readWriteMapView;
    }
    public Map<EventData, Set<EventData>> getWriteReadsMap() {
        return writeReadsMapView;
    }
    public Map<String, Set<EventData>> getFenceMap() {
        return fenceMapView;
    }
    public Map<BigInteger, Set<EventData>> getAddressReadsMap() {
        return addressReadsMapView;
    }
    public Map<BigInteger, Set<EventData>> getAddressWritesMap() {
        return addressWritesMapView;
    }
    public Map<BigInteger, EventData> getAddressInitMap() {
        return addressInitMapView;
    }
    public Map<BigInteger, List<EventData>> getCoherenceMap() { return coherenceMapView; }



    public boolean eventExists(Event e) {
        return eventMap.contains(e);
    }

    public Optional<EventData> getData(Event e) {
        return Optional.ofNullable(eventMap.get(e));
    }

    //========================== Initialization =========================

    public void initialize() {
        List<Init> iniEvents = getProgram().getThreadEvents(Init.class);
        List<Map.Entry<Init, Integer>> iniIdMap = new ArrayList<>();
        iniEvents.forEach(iniEvent -> iniIdMap.add(new AbstractMap.SimpleImmutableEntry<>(iniEvent, -1)));
        initialize(iniIdMap, null);
    }

    public void initialize(List<EventData> events, Event read, Event write) {
        List<Map.Entry<Event, Integer>> eventIdMap = new ArrayList<>();
        events.forEach(event -> eventIdMap.add(new AbstractMap.SimpleImmutableEntry<>(event.getEvent(), event.getLocalId())));

        Map<Event, Event> readWriteMap = new HashMap<>();
        readWriteMap.put(read, write);
        events.forEach(event -> readWriteMap.put(event.getEvent(), event.getReadFrom().getEvent()));

        initialize(eventIdMap, readWriteMap);
    }

    public void initialize(List<? extends Map.Entry<? extends Event, Integer>> events, Map<Event, Event> readWriteMap) {
        // We populate here, instead of on construction,
        // to reuse allocated data structures (since these data structures already adapted
        // their capacity in previous iterations and thus we should have less overhead in future populations)
        // However, for all intents and purposes, this serves as a constructor.
        eventList.clear();
        threadList.clear();
        threadEventsMap.clear();
        atomicBlocksMap.clear();
        addressInitMap.clear(); // This one can probably be constant and need not be rebuilt!
        addressWritesMap.clear();
        addressReadsMap.clear();
        writeReadsMap.clear();
        fenceMap.clear();
        eventMap.clear();
        coherenceMap.clear();

        localThreadList = new ArrayList<>(getProgram().getThreads().size());
        threadEndIndexList = new ArrayList<>(getProgram().getThreads().size());
        atomicBlockRangesMap = new HashMap<>();
        id = -1;
        threadIterator = getProgram().getThreads().iterator();
        thread = threadIterator.next();
        e = null;
        atomicBegin = -1;
        localId = -1;

        for (var eventEntry : events) {
            Event event = eventEntry.getKey();
            EventData eventData = eventMap.get(event);
            if (eventData != null) {
                eventList.remove(eventData);
                eventList.add(eventData);
                continue;
            }

            int localId = eventEntry.getValue();
            if (event.hasTag(READ)) {
                Event writeEvent = readWriteMap.get(event);
                EventData readFrom = eventMap.get(writeEvent);
                if (readFrom == null) {
                    int writeLocalId = events.stream()
                            .filter(entry -> entry.getKey() == writeEvent)
                            .findAny()
                            .get()
                            .getValue();
                    readFrom = addEvent(writeEvent, writeLocalId, null);
                    addEvent(event, localId, readFrom);
                } else {
                    addEvent(event, localId, readFrom);
                }
            } else {
                addEvent(event, localId, null);
            }
        }
    }

    //========================== Public methods =========================

    private List<Thread> localThreadList;
    private List<Integer> threadEndIndexList;
    private Map<Thread, List<List<Integer>>> atomicBlockRangesMap;
    private int id;
    private Iterator<Thread> threadIterator;
    private Thread thread;
    private List<List<Integer>> atomicBlockRanges;
    private Event e;
    private int atomicBegin;
    private int localId;

    public Event nextEvent() {
        if (e == null && threadIterator.hasNext()) {
            // We have a BeginAtomic without EndAtomic since the program terminated within the block
            if (atomicBegin != -1) {
                atomicBlockRanges.add(ImmutableList.of(atomicBegin, id + 1));
            }
            // -----------
            threadEndIndexList.add(id + 1);

            // Next thread
            thread = threadIterator.next();
            localThreadList.add(thread);
            atomicBlockRanges = atomicBlockRangesMap.computeIfAbsent(thread, key -> new ArrayList<>());
            e = thread.getEntry();
            atomicBegin = -1;
            localId = -1;
        }
        try {
            while (e != null) {
                // ===== Atomic blocks =====
                if (e instanceof BeginAtomic) {
                    atomicBegin = id + 1;
                } else if (e instanceof EndAtomic) {
                    Preconditions.checkState(atomicBegin != -1, "EndAtomic without matching BeginAtomic in model");
                    atomicBlockRanges.add(ImmutableList.of(atomicBegin, id + 1));
                    atomicBegin = -1;
                }
                // =========================
    
                Event foundEvent = e;
    
                if (e instanceof CondJump jump && evaluateBool(jump.getGuard())) {
                    e = jump.getLabel();
                } else {
                    e = e.getSuccessor();
                }
    
                id++;
                localId++;
                EventData foundEventData = eventMap.get(foundEvent);
                if (foundEventData != null) {
                    foundEventData.setId(id);
                    foundEventData.setLocalId(localId);
                } else return foundEvent;
            }
        } finally {
            if (e != null) {
                // We have a BeginAtomic without EndAtomic since the program terminated within the block
                if (atomicBegin != -1) {
                    atomicBlockRanges.add(ImmutableList.of(atomicBegin, id + 1));
                }
                // -----------
                threadEndIndexList.add(id + 1);
            }

            threadEndIndexList.remove((Object) 0);
            // Get sublists for all threads
            int start = 0;
            for (int i = 0; i < localThreadList.size(); i++) {
                Thread thread = localThreadList.get(i);
                int end = threadEndIndexList.get(i);
                if (start != end) {
                    this.threadList.add(thread);
                    threadEventsMap.put(thread, List.copyOf(eventList.subList(start, end)));

                    atomicBlocksMap.put(thread, new ArrayList<>());
                    for (List<Integer> aRange : atomicBlockRangesMap.get(thread)) {
                        atomicBlocksMap.get(thread).add(eventList.subList(aRange.get(0), aRange.get(1)));
                    }
                }
                start = end;
            }

            if (e != null) {
                threadEndIndexList.removeLast();
            }
        }
        return nextEvent();
    }

    public EventData addEvent(Event e) {
        return addEvent(e, localId, null);
    }

    public void setRfEdge(EventData readEvent, EventData writeEvent) {
        readWriteMap.put(readEvent, writeEvent);
        readEvent.setReadFrom(writeEvent);
        writeReadsMap.get(writeEvent).add(readEvent);
    }

    public boolean isConsistent(GenMCWMMSolver consistencyChecker) {
        return consistencyChecker.check(new GenMCEventDomain(this)) == CONSISTENT;
    }

    public Set<EventData> getPorfPrefix(EventData event) {
        Set<EventData> porfPrefix = getPorfPrefixPreviousPo(event);
        porfPrefix.addAll(getPorfPrefixPreviousRf(event));
        porfPrefix.add(event);
        return porfPrefix;
    }

    public Set<Event> getEventsAfter(Event splitEvent) {
        Set<Event> events = new HashSet<>();
        boolean canBeAdded = false;
        for (EventData event : eventList) {
            if (event.getEvent() == splitEvent) {
                canBeAdded = true;
            } else if (canBeAdded) {
                events.add(event.getEvent());
            }
        }
        return events;
    }

    //========================== Internal methods  =========================

    private EventData addEvent(Event e, int localId, EventData readFrom) {
        EventData data = eventMap.getOrCreate(e);
        data.setId(id);
        data.setLocalId(localId);
        eventList.add(data);

        data.setWasExecuted(true);
        if (data.isMemoryEvent()) {
            // ===== Memory Events =====
            BigInteger address = checkNotNull(evaluateInt(address((MemoryCoreEvent) e)));
            data.setAccessedAddress(address);
            if (!addressReadsMap.containsKey(address)) {
                addressReadsMap.put(address, new HashSet<>());
                addressWritesMap.put(address, new HashSet<>());
            }

            if (data.isRead() || data.isWrite()) {
                Object valueFormula = value((MemoryCoreEvent) e, data, null);
                assert valueFormula != null;
                String valueString = String.valueOf(valueFormula);
                BigInteger value = switch(valueString) {
                    case "false" -> BigInteger.ZERO;
                    case "true" -> BigInteger.ONE;
                    default -> new BigInteger(valueString);
                };
                data.setValue(value);
            }

            if (data.isRead()) {
                addressReadsMap.get(address).add(data);
            } else if (data.isWrite()) {
                addressWritesMap.get(address).add(data);
                writeReadsMap.put(data, new HashSet<>());
                if (data.isInit()) {
                    addressInitMap.put(address, data);
                }
            } else {
                //FIXME: Handle other kinds of memory events such as SRCU_SYNC.
                throw new UnsupportedOperationException("Unexpected memory event " + data.getEvent());
            }

        } else if (data.isFence()) {
            // ===== Fences =====
            // FIXME this assumes not only that the event has a fence tag but also that it
            // is a GenericVisibleEvent. This is dangerous and should be fixed.
            String name = ((GenericVisibleEvent)data.getEvent()).getName();
            fenceMap.computeIfAbsent(name, key -> new HashSet<>()).add(data);
        } else if (data.isJump()) {
            // ===== Jumps =====
            // We override the meaning of execution here. A jump is executed IFF its condition was true.
            data.setWasExecuted(evaluateBool(((CondJump) e).getGuard()));
        } else if (data.getEvent().getClass().equals(Assert.class)) {
            Assert assertion = (Assert) data.getEvent();
            Boolean asserted = evaluateBool(assertion.getExpression());
            data.setValue(asserted ? BigInteger.ONE : BigInteger.ZERO);
            return null;
        } else {
            //TODO: Maybe add some other events
            // But for now all non-visible events are simply registered without
            // having any data extracted
        }
        return data;
    }

    private Set<EventData> getPorfPrefixPreviousPo(EventData event) {
        var previousEvent = threadEventsMap.get(event.getThread())
                .stream()
                .filter((otherEvent -> otherEvent.getLocalId() == event.getLocalId() - 1))
                .findAny();
        if (previousEvent.isPresent()) {
            return getPorfPrefix(previousEvent.get());
        }
        return Collections.emptySet();
    }

    private Set<EventData> getPorfPrefixPreviousRf(EventData event) {
        if (event.isRead()) {
            return getPorfPrefix(event.getReadFrom());
        }
        return Collections.emptySet();
    }

    // ===================================================

    /*private void extractCoherences() {
        final EncodingContext.EdgeEncoder co = encodingContext.edge(encodingContext.getTask().getMemoryModel().getRelation(CO));

        for (Map.Entry<BigInteger, Set<EventData>> addrWrites : addressWritesMap.entrySet()) {
            final BigInteger addr = addrWrites.getKey();
            final Set<EventData> writes = addrWrites.getValue();

            List<EventData> coSortedWrites;
            if (encodingContext.usesSATEncoding()) {
                // --- Extracting co from SAT-based encoding ---
                Map<EventData, List<EventData>> coEdges = new HashMap<>();
                for (EventData w1 : writes) {
                    coEdges.put(w1, new ArrayList<>());
                    for (EventData w2 : writes) {
                        if (isTrue(co.encode(w1.getEvent(), w2.getEvent()))) {
                            coEdges.get(w1).add(w2);
                        }
                    }
                }
                DependencyGraph<EventData> depGraph = DependencyGraph.from(writes, coEdges);
                coSortedWrites = new ArrayList<>(Lists.reverse(depGraph.getNodeContents()));
            } else {
                // --- Extracting co from IDL-based encoding using clock variables ---
                Map<EventData, BigInteger> writeClockMap = new HashMap<>(writes.size() * 4 / 3, 0.75f);
                for (EventData w : writes) {
                    writeClockMap.put(w, model.evaluate(encodingContext.memoryOrderClock(w.getEvent())));
                }
                coSortedWrites = writes.stream().sorted(Comparator.comparing(writeClockMap::get)).collect(Collectors.toList());
            }

            // --- Apply internal clock orders (we always start from 0) --
            int i = 0;
            for (EventData w : coSortedWrites) {
                w.setCoherenceIndex(i++);
            }
            coherenceMap.put(addr, Collections.unmodifiableList(coSortedWrites));
        }

    }*/

    private Object evaluate(Expression expression) {
        try {
            return evaluateBool(expression);
        } catch (UnsupportedOperationException e) {
        }
        try {
            return evaluateInt(expression);
        } catch (UnsupportedOperationException e) {
        }
        try {
            return evaluateFloat(expression);
        } catch (UnsupportedOperationException e) {
        }
        return null;
    }

    private Boolean evaluateBool(Expression expression) {
        return expression.accept(new BoolExpressionEvaluator());
    }

    private BigDecimal evaluateFloat(Expression expression) {
        return expression.accept(new FloatExpressionEvaluator());
    }

    private BigInteger evaluateInt(Expression expression) {
        return expression.accept(new IntExpressionEvaluator());
    }

    private Expression address(MemoryCoreEvent e) {
        return e.getAddress();
    }

    private Object value(MemoryCoreEvent e, EventData data, EventData readFrom) {
        if (data.isWrite()) {
            return evaluate(((Store) e).getMemValue());
        } else if (data.isRead()) {
            if (readFrom == null) {
                readFrom = addressWritesMap.get(data.getAccessedAddress())
                        .stream()
                        .findAny()
                        .get();
            }
            setRfEdge(data, readFrom);
            return readFrom.getValue();
        }
        return null;
    }
}
