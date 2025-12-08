package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.configuration.GenMCReasoning;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.threading.ThreadJoin;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.solver.caat4wmm.PatternManager;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.AddressLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.CoreLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.ExecLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.RelLiteral;
import com.dat3m.dartagnan.utils.Timer;
import com.dat3m.dartagnan.utils.logic.Conjunction;
import com.dat3m.dartagnan.utils.logic.DNF;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;

import com.dat3m.dartagnan.wmm.utils.graph.EventGraph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.SolverException;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;

import static com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator.ExprEvaluationContext;
import static com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain.getOptimalThreadMapCapacity;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.*;
import static java.util.stream.Collectors.toMap;

/*
The ExecutionModel wraps a Model and extracts data from it in a more workable manner.
 */

//TODO: Add the capability to remove unnecessary init events from a model
// i.e. those that init some address which no read nor write accesses.
@Options
public class GenMCExecutionModel {

    private static final Logger logger = LogManager.getLogger(GenMCExecutionModel.class);
    private static final ExpressionFactory expressions = ExpressionFactory.getInstance();

    private static Timer consistencyCheckTimer = new Timer();
    private static Timer reasonEvaluationTimer = new Timer();
    private static Timer patternEvaluationTimer = new Timer();

    private static int consistencyChecks;
    private static Map<Event, List<Conjunction<CoreLiteral>>> reasons;
    private static Relation rf;
    private static Relation co;
    private static Relation lxsx;
    private static Map<Event, Set<Event>> mayRfInMap;
    private static Map<Event, Set<Event>> mayRfOutMap;
    private static Map<Event, Set<Event>> mayCoInMap;
    private static Map<Event, Set<Event>> mayCoOutMap;
    private static Map<Event, Event> mayLxsxMap;
    private static List<Thread> relevantThreads;
    private static Comparator<EventData> explorationOrder;
    private static Map<Event, Map<Thread, Integer>> porfPrefixes;
    private static int optimalPorfPrefixCapacity;
    private static Set<Event> failedJoinAssumes;

    // ============= Model specific  =============
    private GenMCEventDomain trace;
    private EventData lastAdded = null;
    private int currentThreadIndex = 0;
    private boolean hasError = false;
    private boolean isConsistent = true;
    private boolean needsNewConsistencyCheck = true;
    private boolean lastNeedsNewConsistencyCheck = true;
    private boolean needsJumpRevisit = false;
    private boolean initiallyPropagated = false;
    private EventData backtrackingPoint = null;
    private boolean backtrackReasonIsRel = true;    // TODO: set for patterns
    private Map<Thread, Integer> porfPrefix;

    private final VerificationTask task;
    private final GenMCWMMSolver solver;
    private final PatternManager patterns;
    private final ExprEvaluationContext evalContext;
    private final GenMCReasoning reasoningMethod;
    private final boolean underApproximateWmm;
    private final ExpressionEvaluator exprEvaluator;

    private final Map<Thread, Event> boundEvents;
    private final Map<Event, List<EventData>> mayRfMap;
    private final List<EventData> addedEvents;

    private GenMCExecutionModel(VerificationTask task, GenMCWMMSolver solver, PatternManager patterns, ExprEvaluationContext evalContext, ExpressionEvaluator exprEvaluator,
                                Map<Thread, Event> boundEvents, GenMCReasoning reasoningMethod, boolean underApproximateWmm) {
        this.task = task;
        this.solver = solver;
        this.patterns = patterns;
        this.evalContext = evalContext;
        this.reasoningMethod = reasoningMethod;
        this.underApproximateWmm = underApproximateWmm;
        this.exprEvaluator = exprEvaluator;

        this.boundEvents = boundEvents;
        this.mayRfMap = new HashMap<>();
        this.addedEvents = new ArrayList<>();
    }

    public static GenMCExecutionModel withTask(VerificationTask task, Context analysisContext, ExprEvaluationContext evalContext, GenMCReasoning reasoningMethod,
                                               boolean underApproximateWmm) throws InvalidConfigurationException {
        consistencyCheckTimer = new Timer();
        reasonEvaluationTimer = new Timer();
        patternEvaluationTimer = new Timer();
        consistencyChecks = 0;
        reasons = new HashMap<>();
        Wmm memoryModel = task.getMemoryModel();
        GenMCWMMSolver solver = GenMCWMMSolver.fromConfig(task.getConfig(), task.getMemoryModel(), analysisContext);
        rf = memoryModel.getRelation(RF);
        co = memoryModel.getRelation(CO);
        lxsx = memoryModel.getRelation(LXSX);
        PatternManager patterns = new PatternManager(solver.getExecutionGraph());
        solver.injectExtractor(patterns);
        ExpressionEvaluator exprEvaluator = ExpressionEvaluator.withContext(evalContext);
        GenMCExecutionModel model = new GenMCExecutionModel(task, solver, patterns, evalContext, exprEvaluator, new HashMap<>(), reasoningMethod, underApproximateWmm);
        RelationAnalysis relations = analysisContext.requires(RelationAnalysis.class);
        EventGraph mayRfGraph = relations.getKnowledge(rf).getMaySet();
        EventGraph mayCoGraph = relations.getKnowledge(co).getMaySet();
        mayRfInMap = mayRfGraph.getInMap();
        mayRfOutMap = mayRfGraph.getOutMap();
        mayCoInMap = mayCoGraph.getInMap();
        mayCoOutMap = mayCoGraph.getOutMap();
        Map<Event, Set<Event>> mayLxsxMap = relations.getKnowledge(lxsx).getMaySet().getInMap();
        if (mayLxsxMap.values().stream().anyMatch(e -> e.size() > 1)) {
            throw new UnsupportedOperationException("Dynamic exclusive load-store relations with GenMC are not supported yet");
        }
        GenMCExecutionModel.mayLxsxMap = mayLxsxMap.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, lxsxPair -> lxsxPair.getValue().iterator().next()));
        relevantThreads = new ArrayList<>(task.getProgram().getThreads());
        if (reasoningMethod.usesReasons() || reasoningMethod.usesPatterns()) {
            explorationOrder = Comparator.comparingInt(EventData::getId);
            if (reasoningMethod.usesPatterns()) {
                explorationOrder = explorationOrder.reversed();
            }
        }
        porfPrefixes = new HashMap<>();
        optimalPorfPrefixCapacity = getOptimalThreadMapCapacity(relevantThreads.size() - 1);
        return model;
    }

    public GenMCExecutionModel newInstance(List<EventData> startTraceEvents, Map<Thread, Event> boundEvents) {
        GenMCExecutionModel model = new GenMCExecutionModel(task, solver, patterns, evalContext, exprEvaluator.newInstance(), boundEvents, reasoningMethod,
                underApproximateWmm);
        int i = 0;
        for (EventData startTraceEvent : startTraceEvents) {
            startTraceEvent.setId(i);
            ++i;
        }
        model.trace = new GenMCEventDomain(startTraceEvents, trace.getThreadEventsMap(), trace.getAddressInits(), trace.getReadWriteMap(),
                trace.getWriteReadsMap(), trace.getLxsxPairs());
        if (reasoningMethod.usesReasoning()) {
            List<EventData> newAddedEvents = addedEvents.stream()
                    .map(EventData::getEvent)
                    .map(model.trace::getData)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            model.addedEvents.addAll(newAddedEvents);
            model.initiallyPropagated = initiallyPropagated;
        }
        return model;
    }

    //======================== Public data ===========================

    // Static data
    public static int getConsistencyChecks() {
        return consistencyChecks;
    }

    public static Timer getConsistencyCheckTimer() {
        return consistencyCheckTimer;
    }

    public static Timer getReasonEvaluationTimer() {
        return reasonEvaluationTimer;
    }

    public static Timer getPatternEvaluationTimer() {
        return patternEvaluationTimer;
    }

    // General data
    public int getSolverChecks() {
        return exprEvaluator.getSolverChecks();
    }

    public Timer getSolverCheckTimer() {
        return exprEvaluator.getSolverCheckTime();
    }

    public Timer getEvaluationTimer() {
        return exprEvaluator.getEvaluationTime();
    }

    public VerificationTask getTask() {
        return task;
    }

    public Program getProgram() {
        return getTask().getProgram();
    }

    // Model specific data
    public GenMCEventDomain getTrace() {
        return trace;
    }
    public boolean hasError() {
        return hasError;
    }
    public boolean isConsistent() {
        return isConsistent;
    }
    public boolean needsNewConsistencyCheck() {
        return needsNewConsistencyCheck;
    }
    public boolean lastNeedsNewConsistencyCheck() {
        return lastNeedsNewConsistencyCheck;
    }
    public boolean needsJumpRevisit() {
        return needsJumpRevisit;
    }
    public Map<Thread, Integer> getPorfPrefix() {
        return porfPrefix;
    }
    public int getOptimalPorfPrefixCapacity() {
        return optimalPorfPrefixCapacity;
    }
    public boolean hasBacktrackingPoint() {
        return backtrackingPoint != null;
    }

    public Collection<EventData> getMayRfSet(EventData event) {
        return mayRfMap.get(event.getEvent());
    }

    public Collection<EventData> getMayCoSet(EventData write) {
        Event writeEvent = write.getEvent();
        BigInteger writeAddress = (BigInteger) write.getAccessedAddress();
        Set<Event> inSet = mayCoInMap.get(writeEvent);
        if (inSet == null) {
            return Collections.emptySet();
        }
        EventData addressInit = trace.getAddressInits().get(writeAddress);
        if (addressInit == null) {
            return Collections.emptySet();
        }
        Set<Event> outSet = mayCoOutMap.get(writeEvent);
        if (outSet == null) {
            EventData coPredecessor = null;
            EventData sameAddressWrite = addressInit;
            while (sameAddressWrite != null) {
                coPredecessor = sameAddressWrite;
                sameAddressWrite = sameAddressWrite.getCoSuccessor();
            }
            return Collections.singleton(coPredecessor);
        }
        List<EventData> mayCoSet = new ArrayList<>();
        EventData sameAddressWrite = addressInit;
        while (sameAddressWrite != null) {
            Event sameAddressEvent = sameAddressWrite.getEvent();
            if (((inSet.contains(sameAddressEvent) && (sameAddressWrite.getCoSuccessor() == null || outSet.contains(sameAddressWrite.getCoSuccessor().getEvent()))))) {
                mayCoSet.add(sameAddressWrite);
            }
            sameAddressWrite = sameAddressWrite.getCoSuccessor();
        }
        applyExplorationOrder(mayCoSet);
        return mayCoSet;
    }

    //========================== Initialization =========================

    public void initialize() throws InterruptedException, SolverException, InvalidConfigurationException {
        List<Init> inits = getProgram().getThreadEvents(Init.class);
        setTrace(new GenMCEventDomain(relevantThreads.size()), null, true);
        for (Init init : inits) {
            Thread initThread = init.getThread();
            relevantThreads.remove(initThread);
            for (Event initThreadEvent : initThread.getEvents()) {
                EventData data = new EventData(initThreadEvent);
                data.setId(trace.size());
                if (initThreadEvent == init) {
                    data.setAccessedAddress(exprEvaluator.evaluate(init.getAddress(), init));
                }
                trace.addEvent(data);
            }
        }
        lastAdded = null;
        currentThreadIndex = 0;
        isConsistent = true;
        needsNewConsistencyCheck = false;
        lastNeedsNewConsistencyCheck = false;
        porfPrefix = new HashMap<>(optimalPorfPrefixCapacity);
        failedJoinAssumes = new HashSet<>(getOptimalThreadMapCapacity(relevantThreads.size() - 1));
    }

    public void initialize(EventData revisitRead, EventData write, boolean needsNewConsistencyCheck) {
        porfPrefix = porfPrefixes.get(revisitRead.getEvent());
        setRf(revisitRead, write);
        setTrace(trace, revisitRead, false);
        initFirstThreadEvent();
        initializeFields(needsNewConsistencyCheck, false);
    }

    //========================== Public methods =========================

    public void setJump(EventData jump, boolean jumpIsExecuted) throws SolverException, InterruptedException, InvalidConfigurationException {
        CondJump jumpEvent = (CondJump) jump.getEvent();
        exprEvaluator.addNonDetCondition(jumpEvent, jumpEvent.getGuard(), jumpIsExecuted);
        needsNewConsistencyCheck = jump.wasExecuted() != jumpIsExecuted;
        jump.setWasExecuted(jumpIsExecuted);
        isConsistent = exprEvaluator.evaluateReachability(jumpIsExecuted ? jumpEvent.getGuard() : expressions.makeNot(jumpEvent.getGuard()), jumpEvent);
    }

    public void setRf(EventData read, EventData write) {
        trace.setReadFrom(read, write, read.getReadFrom());
        read.setReadFrom(write);
        Event writeEvent = write.getEvent();
        Thread writeThread = writeEvent.getThread();
        Integer porfPrefixInWriteThread = porfPrefix.get(writeThread);
        int writeGid = writeEvent.getGlobalId();
        if (porfPrefixInWriteThread == null || writeGid > porfPrefixInWriteThread) {
            porfPrefix.put(writeThread, writeGid);
        }
        Event readEvent = read.getEvent();
        porfPrefixes.put(readEvent, new HashMap<>(porfPrefix));
        addedEvents.add(read);
        List<EventData> mayRfSet = mayRfMap.get(readEvent);
        if ((mayRfSet == null || mayRfSet.size() > 1) && write.getCoSuccessor() != null) {
            needsNewConsistencyCheck = true;
        }
    }

    public void setCo(EventData write, EventData newWrite, Collection<EventData> mayCoSet) {
        if (write.getCoSuccessor() == newWrite) {
            return;
        }
        newWrite.setCoPredecessor(write);
        newWrite.setCoSuccessor(write.getCoSuccessor());
        write.setCoSuccessor(newWrite);
        if (newWrite.getCoSuccessor() != null) {
            newWrite.getCoSuccessor().setCoPredecessor(newWrite);
        }
        addedEvents.add(newWrite);
        if (mayCoSet == null || mayCoSet.size() > 1) {
            if (newWrite.getCoSuccessor() == null) {
                Event newWriteEvent = newWrite.getEvent();
                EventData rmwLoad;
                if (newWriteEvent instanceof RMWStore rmwStore) {
                    rmwLoad = getDataFromRmwLoad(rmwStore);
                } else if (newWriteEvent instanceof RMWStoreExclusive rmwStore) {
                    rmwLoad = getDataFromRmwLoad(rmwStore);
                } else return;
                if (rmwLoad.getReadFrom() == write) {
                    return;
                }
            }
            needsNewConsistencyCheck = true;
        }
    }

    public void rollback(EventData rollbackPoint, boolean needsNewConsistencyCheck, boolean lastNeedsNewConsistencyCheck) {
        lastAdded = rollbackPoint;
        Event backtrackEvent = rollbackPoint.getEvent();
        currentThreadIndex = relevantThreads.indexOf(backtrackEvent.getThread());
        backtrackTrace(rollbackPoint);
        if (rollbackPoint.getReadFrom() != null) {
            porfPrefix = porfPrefixes.get(backtrackEvent);
        }
        addedEvents.remove(rollbackPoint);
        setTrace(trace, rollbackPoint, true);
        initializeFields(needsNewConsistencyCheck, lastNeedsNewConsistencyCheck);
        if (backtrackingPoint != null && rollbackPoint.getId() <= backtrackingPoint.getId()) {
            if (backtrackReasonIsRel || rollbackPoint != backtrackingPoint) {
                backtrackingPoint = null;
            }
        }
    }

    public void rollbackCo(EventData coPredecessor, EventData rollbackPoint, boolean forBackwardRevisit,
                           boolean needsNewConsistencyCheck, boolean lastNeedsNewConsistencyCheck) {
        backtrackTrace(rollbackPoint);
        coPredecessor.setCoSuccessor(rollbackPoint.getCoSuccessor());
        if (coPredecessor.getCoSuccessor() != null) {
            coPredecessor.getCoSuccessor().setCoPredecessor(coPredecessor);
        }
        rollbackPoint.setCoPredecessor(null);
        rollbackPoint.setCoSuccessor(null);
        addedEvents.remove(rollbackPoint);
        setTrace(trace, rollbackPoint, true);
        if (forBackwardRevisit) {
            initFirstThreadEvent();
        } else {
            lastAdded = rollbackPoint;
            currentThreadIndex = relevantThreads.indexOf(rollbackPoint.getThread());
        }
        initializeFields(needsNewConsistencyCheck, lastNeedsNewConsistencyCheck);
        if (backtrackingPoint != null && rollbackPoint.getId() <= backtrackingPoint.getId()) {
            if (backtrackReasonIsRel || rollbackPoint != backtrackingPoint) {
                backtrackingPoint = null;
            }
        }
    }

    public void resetConsistencyStatus() {
        isConsistent = true;
    }

    public boolean checkConsistency(boolean ignoreLast) {
        if (reasoningMethod.usesReasoning()) {
            if (backtrackingPoint != null) {
                return false;
            }
        }
        if ((!needsNewConsistencyCheck && !ignoreLast) || (!lastNeedsNewConsistencyCheck && ignoreLast)) {
            return isConsistent;
        }
        EventData ignoredEvent = null;
        if (ignoreLast) {
            ignoredEvent = trace.removeLast();
            if (reasoningMethod.usesReasoning()) {
                addedEvents.remove(ignoredEvent);
            }
        }
        if (!underApproximateWmm) {
            isConsistent &= checkFullConsistency();
        } else {
            isConsistent &= !reasonKnown() && !hasPattern();
        }
        if (isConsistent) {
            if (ignoredEvent != null) {
                lastNeedsNewConsistencyCheck = false;
            } else {
                needsNewConsistencyCheck = false;
            }
        }
        if (ignoredEvent != null) {
            trace.addEvent(ignoredEvent);
            checkForExclusiveLoad(ignoredEvent);
            if (reasoningMethod.usesReasoning()) {
                addedEvents.add(ignoredEvent);
            }
        }
        return isConsistent;
    }

    public boolean checkFullConsistency() {
        if (reasoningMethod.usesReasoning()) {
            if (backtrackingPoint != null) {
                return false;
            }
        }
        if (reasonKnown() || hasPattern()) {
            return false;
        }

        logger.debug("Checking consistency");
        ++consistencyChecks;
        GenMCWMMSolver.Result result = consistencyCheckTimer.measure(() -> solver.check(trace, reasoningMethod));
        addedEvents.clear();
        initiallyPropagated = true;
        if (result.getStatus() == CAATSolver.Status.CONSISTENT) {
            return true;
        } else if (result.getStatus() == CAATSolver.Status.INCONSISTENT) {
            isConsistent = false;
            addReasons(result.getCoreReasons());
            return false;
        } else {
            throw new IllegalArgumentException("Inconclusive");
        }
    }

    public boolean checkProperties() throws InterruptedException, SolverException, InvalidConfigurationException {
        BooleanFormula properties = evalContext.getProperties();
        if (properties == null) {
            logger.debug("No global properties to check");
            return true;
        }
        GenMCTraceEncoder traceEncoder = evalContext.getTraceEncoder();
        BooleanFormulaManager bmgr = traceEncoder.getEncodingContext().getBooleanFormulaManager();
        return !checkProperties(bmgr.and(properties, traceEncoder.getPropertyRelevantEncoding()));
    }

    public void updateBacktrackingPoint(GenMCExecutionModel backwardRevisitModel, Event revisitWrite) {
        if (backtrackingPoint == null) {
            EventData backwardRevisitBacktrackingPoint = backwardRevisitModel.backtrackingPoint;
            if (backwardRevisitBacktrackingPoint != null) {
                Event backwardRevisitBacktrackingPointEvent = backwardRevisitBacktrackingPoint.getEvent();
                boolean backwardRevisitBacktrackReasonIsRel = backwardRevisitModel.backtrackReasonIsRel;
                if (!(backwardRevisitBacktrackReasonIsRel && backwardRevisitBacktrackingPointEvent == revisitWrite)) {
                    backtrackingPoint = trace.getData(backwardRevisitBacktrackingPointEvent).orElse(null);
                    backtrackReasonIsRel = backwardRevisitBacktrackReasonIsRel;
                }
            }
        }
    }

    public List<EventData> copyTraceEvents(Predicate<EventData> filter) {
        List<EventData> events = trace.getElements();
        EventMap eventMap = new EventMap();
        List<EventData> copiedEvents = new ArrayList<>(events.size());
        events.stream().filter(filter).forEach(event -> {
            EventData copiedEvent = eventMap.getOrCreate(event.getEvent());
            copiedEvent.setReadFrom(event.getReadFrom());
            copiedEvent.setCoPredecessor(event.getCoPredecessor());
            copiedEvent.setCoSuccessor(event.getCoSuccessor());
            copiedEvent.setWasExecuted(event.wasExecuted());
            copiedEvent.setNonDet(event.isNonDet());
            copiedEvent.setAccessedAddress(event.getAccessedAddress());
            copiedEvent.setId(event.getId());
            copiedEvent.setLocalId(event.getLocalId());
            copiedEvents.add(copiedEvent);
        });
        for (EventData event : copiedEvents) {
            if (event.getReadFrom() != null) {
                EventData newReadFrom = eventMap.get(event.getReadFrom().getEvent());
                event.setReadFrom(newReadFrom);
            }
            EventData coPredecessor = event.getCoPredecessor();
            while (coPredecessor != null) {
                EventData copiedCoPredecessor = eventMap.get(coPredecessor.getEvent());
                if (copiedCoPredecessor != null) {
                    event.setCoPredecessor(copiedCoPredecessor);
                    break;
                }
                coPredecessor = coPredecessor.getCoPredecessor();
            }
            if (coPredecessor == null) {
                event.setCoPredecessor(null);
            }
            EventData coSuccessor = event.getCoSuccessor();
            while (coSuccessor != null) {
                EventData copiedCoSuccessor = eventMap.get(coSuccessor.getEvent());
                if (copiedCoSuccessor != null) {
                    event.setCoSuccessor(copiedCoSuccessor);
                    break;
                }
                coSuccessor = coSuccessor.getCoSuccessor();
            }
            if (coSuccessor == null) {
                event.setCoSuccessor(null);
            }
        }
        return copiedEvents;
    }

    public EventData nextEvent() throws InterruptedException, SolverException, InvalidConfigurationException {
        EventData startEvent = null;
        boolean needsInit = true;
        if (lastAdded != null) {
            startEvent = lastAdded;
            needsInit = false;
        }
        boolean newThread = false;
        while (currentThreadIndex < relevantThreads.size()) {
            Thread thread = relevantThreads.get(currentThreadIndex);
            if (newThread) {
                Event boundEvent = boundEvents.get(thread);
                porfPrefix = boundEvent == null ? new HashMap<>(optimalPorfPrefixCapacity) : porfPrefixes.get(boundEvent);
            }
            Event e;
            if (needsInit) {
                Optional<EventData> eventData = trace.getLastAddedThreadEventData(thread);
                if (eventData.isPresent()) {
                    e = eventData.get().getEvent().getSuccessor();
                } else {
                    e = thread.getEntry();
                }
            } else {
                e = startEvent.getEvent();
                if (e instanceof CondJump jump && startEvent.wasExecuted()) {
                    e = jump.getLabel();
                } else {
                    e = e.getSuccessor();
                }
            }
            needsInit = true;
            while (e != null) {
                if (e instanceof ControlBarrier) {
                    throw new UnsupportedOperationException("ControlBarrier not yet implemented for GenMC");
                }
                EventData event = addEvent(e);
                lastAdded = event;
                e = lastAdded.getEvent();
                if (e instanceof MemoryEvent || e instanceof CondJump) {
                    return event;
                }
                e = e.getSuccessor();
            }
            ++currentThreadIndex;
            newThread = true;
        }
        return null;
    }

    public boolean isFinished() {
        return failedJoinAssumes.isEmpty();
    }

    //========================== Internal methods  =========================

    private void initializeFields(boolean needsNewConsistencyCheck, boolean lastNeedsNewConsistencyCheck) {
        isConsistent = true;
        this.needsNewConsistencyCheck = needsNewConsistencyCheck;
        this.lastNeedsNewConsistencyCheck = lastNeedsNewConsistencyCheck;
    }

    private void initFirstThreadEvent() {
        lastAdded = trace.getLastAddedThreadEventData(relevantThreads.get(0)).orElse(null);
        currentThreadIndex = 0;
    }

    private EventData addEvent(Event e) throws InterruptedException, SolverException, InvalidConfigurationException {
        needsJumpRevisit = false;
        EventData data = new EventData(e);
        data.setId(trace.size());

        data.setWasExecuted(true);
        if (e instanceof MemoryEvent) {
            // ===== Memory Events =====
            BigInteger address = exprEvaluator.evaluateAddress(data);
            if (address == null || BigInteger.ZERO.equals(address)) {
                isConsistent = false;
                trace.addEvent(data);
                return data;
            }
            data.setAccessedAddress(address);
            List<EventData> mayRfSet;
            if (trace.getLast().getEvent() instanceof ThreadStart start && start.isSpawned()) {
                mayRfSet = new ArrayList<>();
                Thread currentThread = e.getThread();
                Optional<EventData> threadStartStore = getThreadStartStore(currentThread);
                if (threadStartStore.isPresent()) {
                    mayRfSet.add(threadStartStore.get());
                } else {
                    return endThread(currentThread);
                }
            } else {
                mayRfSet = mayRfSet(data, address);
            }
            mayRfMap.put(data.getEvent(), mayRfSet);
            trace.addEvent(data);
            checkForExclusiveLoad(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        } else if (data.isJump()) {
            // ===== Jumps =====
            // We override the meaning of execution here. A jump is executed IFF its condition was true.
            CondJump jump = (CondJump) e;
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
            if (jump.getGuard() instanceof BoolLiteral bool) {
                data.setWasExecuted(bool.getValue());
            } else {
                BigInteger guardResult = exprEvaluator.evaluate(jump.getGuard(), jump);
                if (guardResult == null) {
                    if (needsNewConsistencyCheck) {
                        boolean isConsistent = checkConsistency(false);
                        if (!isConsistent) {
                            this.isConsistent = false;
                            return data;
                        }
                    }
                    BigInteger result = exprEvaluator.evaluateNonDet(jump.getGuard(), jump);
                    if (result == null) {
                        isConsistent = false;
                        return data;
                    }
                    boolean wasExecuted = !BigInteger.ZERO.equals(result);
                    logger.debug("Exploring non-deterministic jump {}", jump);
                    data.setWasExecuted(wasExecuted);
                    exprEvaluator.addNonDetCondition(jump, jump.getGuard(), wasExecuted);
                    needsJumpRevisit = true;
                } else {
                    data.setWasExecuted(!BigInteger.ZERO.equals(guardResult));
                }
            }
            trace.addEvent(data);
        } else if (data.getEvent() instanceof Assume assumptionEvent) {
            // ===== Assumptions =====
            Event lastEvent = e.getPredecessor();
            if (!(lastEvent != null && lastEvent.getPredecessor() instanceof ThreadStart start && start.isSpawned())) {
                trace.addEvent(data);
                lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
                Expression assumption = assumptionEvent.getExpr();
                if (assumption instanceof BoolLiteral bool) {
                    isConsistent = bool.getValue();
                } else {
                    isConsistent = exprEvaluator.evaluateReachability(assumption, assumptionEvent);
                    if (lastEvent != null && lastEvent.getPredecessor() instanceof ThreadJoin) {
                        if (!isConsistent) {
                            failedJoinAssumes.add(e);
                            trace.removeLast();
                            isConsistent = true;
                            return endThread(e.getThread());
                        } else {
                            failedJoinAssumes.remove(e);
                        }
                    }
                }
            }
        } else if (data.getEvent() instanceof Assert assertionEvent) {
            // ===== Assertions =====
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
            if (assertionEvent.getExpression() instanceof BoolLiteral bool) {
                boolean failure = !bool.getValue();
                if (failure && checkFullConsistency()) {
                    hasError = true;
                }
            } else {
                hasError = checkProperties(assertionEvent);
            }
        } else {
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        }
        return data;
    }

    private List<EventData> mayRfSet(EventData target, BigInteger address) {
        Map<Event, Set<Event>> mayRfMap;
        Event targetEvent = target.getEvent();
        boolean isWrite = targetEvent instanceof Store;
        if (isWrite) {
            mayRfMap = mayRfOutMap;
        } else {
            mayRfMap = mayRfInMap;
        }
        Set<Event> mayRfSet = mayRfMap.get(targetEvent);
        if (mayRfSet == null) {
            return Collections.emptyList();
        }
        List<EventData> mayRfSetData = new ArrayList<>();
        for (Event event : mayRfSet) {
            trace.getData(event).ifPresent(eventData -> {
                if (address.equals(eventData.getAccessedAddress())) {
                    mayRfSetData.add(eventData);
                }
            });
        }
        if (!isWrite) {
            applyExplorationOrder(mayRfSetData);
        }
        return mayRfSetData;
    }

    private void applyExplorationOrder(List<EventData> maySet) {
        if (reasoningMethod.usesReasoning()) {
            maySet.sort(explorationOrder);
        }
    }

    private Optional<EventData> getThreadStartStore(Thread thread) {
        Optional<EventData> threadCreator = trace.getData(thread.getEntry().getCreator());
        return threadCreator.map(creator -> trace.getObjectById(trace.getId(creator) - 1));
    }

    private EventData endThread(Thread thread) {
        EventData threadExit = new EventData(thread.getExit());
        threadExit.setId(trace.size());
        trace.addEvent(threadExit);
        lastAdded = threadExit;
        lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        return threadExit;
    }

    private void checkForExclusiveLoad(EventData event) {
        if (event.getEvent() instanceof RMWStoreExclusive exStore) {
            trace.addLxsxPair(event, getDataFromRmwLoad(exStore));
        }
    }

    private EventData getDataFromRmwLoad(RMWStore rmwStore) {
        return trace.getData(rmwStore.getLoadEvent())
                .orElseThrow(() -> new IllegalArgumentException("RMW load for event " + rmwStore.getGlobalId() + ": " + rmwStore + " is not in trace yet"));
    }

    private EventData getDataFromRmwLoad(RMWStoreExclusive rmwStore) {
        return trace.getData(mayLxsxMap.get(rmwStore))
                .orElseThrow(() -> new IllegalArgumentException("Exclusive load for event " + rmwStore.getGlobalId() + ": " + rmwStore + " is not in trace yet"));
    }

    private void backtrackTrace(EventData backtrackingPoint) {
        List<EventData> removedEvents = trace.rollback(backtrackingPoint);
        if (reasoningMethod.usesReasoning()) {
            addedEvents.removeAll(removedEvents);
        }
        removedEvents.clear();
    }

    private boolean reasonKnown() {
        if (reasoningMethod.usesReasons()) {
            reasonEvaluationTimer.start();
            EVENT_LOOP:
            for (EventData event : addedEvents) {
                List<Conjunction<CoreLiteral>> reasonsForEvent = reasons.get(event.getEvent());
                if (reasonsForEvent != null) {
                    for (Conjunction<CoreLiteral> reason : reasonsForEvent) {
                        boolean reasonKnown = true;
                        for (CoreLiteral coreLiteral : reason.getLiterals()) {
                            if (!evaluate(coreLiteral, event)) {
                                reasonKnown = false;
                                break;
                            }
                        }
                        if (reasonKnown) {
                            int backtrackingPointId = -1;
                            for (CoreLiteral lit : reason.getLiterals()) {
                                for (Event litEvent : getEvents(lit)) {
                                    EventData eventData = trace.getData(litEvent).orElseThrow(() ->
                                            new IllegalStateException("Event " + litEvent + " is not in trace yet but occurs in reason cube " + reason + " that is fulfilled"));
                                    int eventId = eventData.getId();
                                    if (eventId > backtrackingPointId || (lit instanceof RelLiteral && eventId == backtrackingPointId)) {
                                        backtrackingPoint = eventData;
                                        backtrackReasonIsRel = lit instanceof RelLiteral;
                                        backtrackingPointId = eventId;
                                    }
                                }
                            }
                            break EVENT_LOOP;
                        }
                    }
                }
            }
            addedEvents.clear();
            reasonEvaluationTimer.stop();
            return backtrackingPoint != null;
        }
        return false;
    }

    private boolean evaluate(CoreLiteral literal, EventData event) {
        if (literal instanceof RelLiteral lit) {
            Relation relation = lit.getRelation();
            if (relation == rf) {
                return evaluateRfLiteral(lit, event);
            } else if (relation == co) {
                return evaluateCoLiteral(lit, event);
            } else if (relation == lxsx) {
                return evaluateLxsxLiteral(lit, event);
            } else {
                throw new IllegalArgumentException("Expected base relation for RelLiteral and not " + relation);
            }
        } else if (literal instanceof ExecLiteral lit) {
            return evaluateExecLiteral(lit, event);
        } else if (literal instanceof AddressLiteral loc) {
            return evaluateAddressLiteral(loc, event);
        } else {
            throw new IllegalArgumentException("CoreLiteral " + literal + " is not supported");
        }
    }

    private boolean evaluateExecLiteral(ExecLiteral exec, EventData event) {
        Event targetEvent = exec.getEvent();
        if (event != null && event.getEvent() == targetEvent) {
            return exec.isPositive();
        }
        boolean execValue = trace.getData(targetEvent).isPresent();
        return exec.isNegative() != execValue;
    }

    private boolean evaluateAddressLiteral(AddressLiteral address, EventData event) {
        Event firstEvent = address.getFirst();
        Event secondEvent = address.getSecond();
        boolean addressValue;
        Event e;
        if (event != null && ((e = event.getEvent()) == firstEvent || e == secondEvent)) {
            if (e == firstEvent) {
                Optional<EventData> second = trace.getData(address.getSecond());
                addressValue = second.filter(eventData -> event.getAccessedAddress().equals(eventData.getAccessedAddress())).isPresent();
            } else {
                Optional<EventData> first = trace.getData(address.getFirst());
                addressValue = first.map(eventData -> eventData.getAccessedAddress().equals(event.getAccessedAddress())).orElse(false);
            }
        } else {
            Optional<EventData> first = trace.getData(firstEvent);
            Optional<EventData> second = trace.getData(secondEvent);
            if (first.isEmpty() || second.isEmpty()) {
                addressValue = false;
            } else {
                addressValue = first.get().getAccessedAddress().equals(second.get().getAccessedAddress());
            }
        }
        return address.isNegative() != addressValue;
    }

    private boolean evaluateCoLiteral(RelLiteral co, EventData event) {
        Event target = co.getTarget();
        boolean coValue = false;
        if (event != null && event.getEvent() == target) {
            EventData cur = event;
            while (cur != null) {
                if (cur.getEvent() == co.getSource()) {
                    coValue = true;
                    break;
                }
                cur = cur.getCoPredecessor();
            }
        } else {
            Optional<EventData> suc = trace.getData(target);
            if (suc.isPresent()) {
                EventData cur = suc.get();
                while (cur != null) {
                    if (cur.getEvent() == co.getSource()) {
                        coValue = true;
                        break;
                    }
                    cur = cur.getCoPredecessor();
                }
            }
        }
        return co.isNegative() != coValue;
    }

    private boolean evaluateRfLiteral(RelLiteral rf, EventData event) {
        Event target = rf.getTarget();
        boolean rfValue;
        if (event != null && event.getEvent() == target) {
            EventData readFrom = event.getReadFrom();
            rfValue = readFrom != null && readFrom.getEvent() == rf.getSource();
        } else {
            Optional<EventData> read = trace.getData(rf.getTarget());
            if (read.isPresent()) {
                EventData readFrom = read.get().getReadFrom();
                rfValue = readFrom != null && readFrom.getEvent() == rf.getSource();
            } else {
                rfValue = false;
            }
        }
        return rf.isNegative() != rfValue;
    }

    private boolean evaluateLxsxLiteral(RelLiteral lxsx, EventData event) {
        boolean lxsxValue;
        Event target = lxsx.getTarget();
        if (event != null && event.getEvent() == target) {
            lxsxValue = trace.getLxsxPairs().get(event).getEvent() == lxsx.getSource();
        } else {
            lxsxValue = trace.getData(target)
                    .filter(eventData -> trace.getLxsxPairs().get(eventData).getEvent() == lxsx.getSource()).isPresent();
        }
        return lxsx.isNegative() != lxsxValue;
    }

    private boolean hasPattern() {
        if (reasoningMethod.usesPatterns()) {
            return patternEvaluationTimer.measure(() -> {
                solver.updateBasePredicates(trace);
                return evaluatePatterns();
            });
        }
        return false;
    }

    private boolean evaluatePatterns() {
        if (!initiallyPropagated) {
            for (Map.Entry<EventData, EventData> rfEntry : trace.getReadWriteMap().entrySet()) {
                if ((backtrackingPoint = patterns.isViolated(rfEntry.getValue(), rfEntry.getKey(), rf)) != null) {
                    return true;
                }
            }
            for (EventData addressInit : trace.getAddressInits().values()) {
                EventData firstOverride = addressInit.getCoSuccessor();
                if (firstOverride != null) {
                    for (EventData coSuccessor : firstOverride.getCoSuccessors()) {
                        if ((backtrackingPoint = patterns.isViolated(addressInit, coSuccessor, co)) != null) {
                            return true;
                        }
                    }
                }
            }
            for (Map.Entry<EventData, EventData> lxsxPair : trace.getLxsxPairs().entrySet()) {
                if ((backtrackingPoint = patterns.isViolated(lxsxPair.getValue(), lxsxPair.getKey(), lxsx)) != null) {
                    return true;
                }
            }
        } else {
            for (EventData event : addedEvents) {
                EventData readFrom = event.getReadFrom();
                if (readFrom != null) {
                    if ((backtrackingPoint = patterns.isViolated(readFrom, event, rf)) != null) {
                        return true;
                    }
                } else {
                    if (event.getEvent() instanceof RMWStoreExclusive) {
                        if ((backtrackingPoint = patterns.isViolated(trace.getLxsxPairs().get(event), event, lxsx)) != null) {
                            return true;
                        }
                    }
                    List<Integer> alreadyCheckedIds = new ArrayList<>();
                    EventData coPredecessor = event.getCoPredecessor();
                    EventData coSuccessor = event.getCoSuccessor();
                    if (coPredecessor != null || coSuccessor != null) {
                        int eventId = event.getId();
                        alreadyCheckedIds.add(eventId);
                        if (coPredecessor != null) {
                            for (EventData anyCoPredecessor : coPredecessor.getCoPredecessors()) {
                                if (!alreadyCheckedIds.contains(anyCoPredecessor.getId())) {
                                    if ((backtrackingPoint = patterns.isViolated(anyCoPredecessor, event, co)) != null) {
                                        return true;
                                    }
                                }
                            }
                        }
                        if (coSuccessor != null) {
                            for (EventData anyCoSuccessor : coSuccessor.getCoSuccessors()) {
                                if (!alreadyCheckedIds.contains(anyCoSuccessor.getId())) {
                                    if ((backtrackingPoint = patterns.isViolated(event, anyCoSuccessor, co)) != null) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    // TODO: This method for patterns
    private void addReasons(DNF<CoreLiteral> newReasons) {
        Map<Thread, Event> maxEventForThreads = new HashMap<>();
        int backtrackingPointId = Integer.MAX_VALUE;
        for (Conjunction<CoreLiteral> cube : newReasons.getCubes()) {
            EventData cubeBacktrackingPoint = null;
            int cubeBacktrackingPointId = -1;
            boolean cubeBacktrackingPointReasonIsRel = true;
            for (CoreLiteral lit : cube.getLiterals()) {
                if (lit instanceof RelLiteral relLit && relLit.getRelation() == rf) {
                    Event read = relLit.getTarget();
                    Thread eventThread = read.getThread();
                    Event maxEventForThread = maxEventForThreads.get(eventThread);
                    if (maxEventForThread == null || maxEventForThread.getGlobalId() < read.getGlobalId()) {
                        maxEventForThreads.put(eventThread, read);
                    }
                    EventData readData = trace.getData(read).orElse(null);
                    EventData writeData = trace.getData(relLit.getSource()).orElse(null);
                    if (readData != null && writeData != null) {
                        EventData maxEvent = readData.getId() > writeData.getId() ? readData : writeData;
                        int eventId = maxEvent.getId();
                        if (eventId >= cubeBacktrackingPointId) {
                            cubeBacktrackingPoint = maxEvent;
                            cubeBacktrackingPointId = eventId;
                            cubeBacktrackingPointReasonIsRel = true;
                        }
                    }
                } else {
                    for (Event event : getEvents(lit)) {
                        if (event instanceof Init) {
                            continue;
                        }
                        Thread eventThread = event.getThread();
                        Event maxEventForThread = maxEventForThreads.get(eventThread);
                        if (maxEventForThread == null || maxEventForThread.getGlobalId() < event.getGlobalId()) {
                            maxEventForThreads.put(eventThread, event);
                            EventData eventData = trace.getData(event).orElse(null);
                            if (eventData != null) {
                                int eventId = eventData.getId();
                                if (eventId > cubeBacktrackingPointId || (lit instanceof RelLiteral && eventId == cubeBacktrackingPointId)) {
                                    cubeBacktrackingPoint = eventData;
                                    cubeBacktrackingPointId = eventId;
                                    cubeBacktrackingPointReasonIsRel = lit instanceof RelLiteral;
                                }
                            }
                        }
                    }
                }
            }
            if (reasonEvaluationTimer.measure(() -> cube.evaluate(lit -> evaluate(lit, null)))) {
                if (cubeBacktrackingPointId < backtrackingPointId) {
                    backtrackingPoint = cubeBacktrackingPoint;
                    backtrackingPointId = cubeBacktrackingPointId;
                    backtrackReasonIsRel = cubeBacktrackingPointReasonIsRel;
                }
            }
            for (Event event : maxEventForThreads.values()) {
                reasons.computeIfAbsent(event, e -> new ArrayList<>()).add(cube);
            }
            maxEventForThreads.clear();
        }
    }

    private static Collection<Event> getEvents(CoreLiteral lit) {
        if (lit instanceof RelLiteral relLit) {
            return List.of(relLit.getTarget(), relLit.getSource());
        }
        if (lit instanceof ExecLiteral execLit) {
            return Collections.singleton(execLit.getEvent());
        }
        if (lit instanceof AddressLiteral addrLit) {
            return List.of(addrLit.getFirst(), addrLit.getSecond());
        }
        throw new IllegalArgumentException("CoreLiteral " + lit + " is not supported");
    }

    @FunctionalInterface
    private interface PropertyChecker<T> {
        boolean check(T properties) throws InterruptedException, SolverException, InvalidConfigurationException;
    }

    private boolean checkProperties(Assert assertion) throws InterruptedException, SolverException, InvalidConfigurationException {
        return checkProperties(expressions.makeNot(assertion.getExpression()),
                properties -> exprEvaluator.evaluateReachability(properties, assertion));
    }

    private boolean checkProperties(BooleanFormula properties) throws InterruptedException, SolverException, InvalidConfigurationException {
        return checkProperties(properties, exprEvaluator::evaluateReachability);
    }

    private <T> boolean checkProperties(T properties, PropertyChecker<T> propertyChecker) throws InterruptedException, SolverException, InvalidConfigurationException {
        logger.debug("Checking properties");
        if (!propertyChecker.check(properties)) {
            return false;
        }
        return checkFullConsistency();
    }

    private void setTrace(GenMCEventDomain trace, EventData changedEvent, boolean inTraceOrder) {
        this.trace = trace;
        exprEvaluator.setTrace(trace, changedEvent, inTraceOrder);
    }
}
