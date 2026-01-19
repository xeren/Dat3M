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
import com.dat3m.dartagnan.program.event.core.annotations.StringAnnotation;
import com.dat3m.dartagnan.program.event.core.threading.*;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.solver.caat4wmm.PatternManager;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.*;
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

import static com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator.ExprEvaluationContext;
import static com.dat3m.dartagnan.utils.Utils.getOptimalMapCapacity;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.*;
import static java.util.stream.Collectors.toMap;

/*
The ExecutionModel wraps a Model and extracts data from it in a more workable manner.
 */

//TODO: Add the capability to remove unnecessary init events from a model
// i.e. those that init some address which no read nor write accesses.
@Options
public final class GenMCExecutionModel {

    private static final Logger logger = LogManager.getLogger(GenMCExecutionModel.class);
    private static final ExpressionFactory expressions = ExpressionFactory.getInstance();

    private static int consistencyChecks;

    private static final Timer consistencyCheckTimer = new Timer();
    private static final Timer reasonEvaluationTimer = new Timer();
    private static final Timer patternEvaluationTimer = new Timer();
    private static final Map<Event, List<GenMCReason>> reasons = new HashMap<>();
    private static final Map<Event, Event[]> mayRfInMap = new HashMap<>();
    private static final Map<Event, Event[]> mayRfOutMap = new HashMap<>();
    private static final Map<Event, Set<Event>> mayCoInMap = new HashMap<>();
    private static final Map<Event, Set<Event>> mayCoOutMap = new HashMap<>();
    private static final Map<Event, Event> mayLxsxMap = new HashMap<>();
    private static final List<Thread> relevantThreads = new ArrayList<>();
    private static final Map<Event, List<Map<Thread, Integer>>> porfPrefixes = new HashMap<>();

    // ============= Model specific  =============
    private EventData lastAdded = null;
    private int currentThreadIndex = 0;
    private boolean hasError = false;
    private boolean isConsistent = true;
    private boolean needsNewConsistencyCheck = true;
    private boolean lastNeedsNewConsistencyCheck = true;
    private boolean needsJumpRevisit = false;
    private EventData backtrackingPoint = null;
    private boolean backtrackReasonIsRel = true;
    private Map<Thread, Integer> porfPrefix;

    private final GenMCEventDomain trace;
    private final VerificationTask task;
    private final GenMCWMMSolver solver;
    private final PatternManager patterns;
    private final ExprEvaluationContext evalContext;
    private final ExpressionEvaluator exprEvaluator;
    private final Relation rf;
    private final Relation co;
    private final Relation lxsx;
    private final Comparator<EventData> explorationOrder;
    private final int optimalPorfPrefixCapacity;
    private final GenMCReasoning reasoningMethod;
    private final boolean underApproximateWmm;

    private final Map<Thread, Event> boundEvents;
    private final Set<Event> failedJoinAssumes;
    private final Map<Event, List<EventData>> mayRfMap;
    private final List<EventData> addedEvents;

    private GenMCExecutionModel(final GenMCEventDomain trace, final VerificationTask task, final GenMCWMMSolver solver, final ExprEvaluationContext evalContext,
                                final ExpressionEvaluator exprEvaluator, final Map<Thread, Event> boundEvents, final Set<Event> failedJoinAssumes,
                                final Relation rf, final Relation co, final Relation lxsx, final Comparator<EventData> explorationOrder,
                                final int optimalPorfPrefixCapacity, final GenMCReasoning reasoningMethod, final boolean underApproximateWmm) {
        this.trace = trace;
        this.task = task;
        this.solver = solver;
        this.patterns = solver.getPatterns();
        this.evalContext = evalContext;
        this.exprEvaluator = exprEvaluator;
        this.rf = rf;
        this.co = co;
        this.lxsx = lxsx;
        this.explorationOrder = explorationOrder;
        this.optimalPorfPrefixCapacity = optimalPorfPrefixCapacity;
        this.reasoningMethod = reasoningMethod;
        this.underApproximateWmm = underApproximateWmm;

        this.boundEvents = boundEvents;
        this.failedJoinAssumes = failedJoinAssumes;
        this.mayRfMap = new HashMap<>();
        this.addedEvents = new ArrayList<>();
    }

    public static GenMCExecutionModel withTask(final VerificationTask task, final Context analysisContext, final ExprEvaluationContext evalContext,
                                               final GenMCReasoning reasoningMethod, final boolean underApproximateWmm) throws InvalidConfigurationException {
        final Program program = task.getProgram();
        if (!program.getThreadEvents(ControlBarrier.class).isEmpty()) {
            throw new UnsupportedOperationException("ControlBarrier not yet implemented for GenMC");
        }

        consistencyChecks = 0;
        consistencyCheckTimer.reset();
        reasonEvaluationTimer.reset();
        patternEvaluationTimer.reset();
        reasons.clear();
        final Wmm memoryModel = task.getMemoryModel();
        Relation rf = memoryModel.getRelation(RF);
        Relation co = memoryModel.getRelation(CO);
        Relation lxsx = memoryModel.getRelation(LXSX);
        final RelationAnalysis relations = analysisContext.requires(RelationAnalysis.class);
        final EventGraph mayRfGraph = relations.getKnowledge(rf).getMaySet();
        final EventGraph mayCoGraph = relations.getKnowledge(co).getMaySet();
        mayRfInMap.clear();
        mayRfInMap.putAll(mayRfGraph.getInMap().entrySet().stream()
                .collect(toMap(Map.Entry::getKey, rfSet -> rfSet.getValue().toArray(Event[]::new))));
        mayRfOutMap.clear();
        mayRfOutMap.putAll(mayRfGraph.getOutMap().entrySet().stream()
                .collect(toMap(Map.Entry::getKey, rfSet -> rfSet.getValue().toArray(Event[]::new))));
        mayCoInMap.clear();
        mayCoInMap.putAll(mayCoGraph.getInMap());
        mayCoOutMap.clear();
        mayCoOutMap.putAll(mayCoGraph.getOutMap());
        final Map<Event, Set<Event>> mayLxsxMap = relations.getKnowledge(lxsx).getMaySet().getInMap();
        if (mayLxsxMap.values().stream().anyMatch(e -> e.size() > 1)) {
            throw new UnsupportedOperationException("Dynamic exclusive load-store relations with GenMC are not supported yet");
        }
        GenMCExecutionModel.mayLxsxMap.clear();
        GenMCExecutionModel.mayLxsxMap.putAll(mayLxsxMap.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, lxsxPair -> lxsxPair.getValue().iterator().next())));
        relevantThreads.clear();
        relevantThreads.addAll(program.getThreads());
        porfPrefixes.clear();

        final GenMCEventDomain trace = new GenMCEventDomain(relevantThreads.size());
        final GenMCWMMSolver solver = GenMCWMMSolver.fromConfig(task.getConfig(), task.getMemoryModel(), analysisContext);
        final ExpressionEvaluator exprEvaluator = ExpressionEvaluator.withContext(evalContext);
        exprEvaluator.setTrace(trace);
        Comparator<EventData> explorationOrder = null;
        if (reasoningMethod.usesReasons() || reasoningMethod.usesPatterns()) {
            explorationOrder = Comparator.comparingInt(EventData::getId);
            if (underApproximateWmm && reasoningMethod.usesPatterns()) {
                explorationOrder = explorationOrder.reversed();
            }
        }
        final int optimalPorfPrefixCapacity = getOptimalMapCapacity(relevantThreads.size() - program.getThreadEvents(Init.class).size() - 1);
        return new GenMCExecutionModel(trace, task, solver, evalContext, exprEvaluator, new HashMap<>(), new HashSet<>(), rf, co, lxsx, explorationOrder,
                optimalPorfPrefixCapacity, reasoningMethod, underApproximateWmm);
    }

    public GenMCExecutionModel newInstance(final List<EventData> eventsToDelete, final Map<Thread, Event> boundEvents) {
        final GenMCEventDomain newTrace = new GenMCEventDomain(trace.getElements(), eventsToDelete, trace.getAddressInits().size(), trace.getLxsxPairs());
        final Set<Event> failedJoinAssumes = new HashSet<>(this.failedJoinAssumes);
        failedJoinAssumes.removeIf(assume -> {
            for (final EventData deletedEvent : eventsToDelete) {
                if (deletedEvent.getEvent() == assume) {
                    return true;
                }
            }
            return false;
        });
        final GenMCExecutionModel model = new GenMCExecutionModel(newTrace, task, solver, evalContext, exprEvaluator.newInstance(), boundEvents, failedJoinAssumes,
                rf, co, lxsx, explorationOrder, optimalPorfPrefixCapacity, reasoningMethod, underApproximateWmm);
        if (reasoningMethod.usesReasoning()) {
            for (final EventData addedEvent : addedEvents) {
                model.trace.getData(addedEvent.getEvent()).ifPresent(model.addedEvents::add);
            }
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

    public Collection<EventData> getMayRfSet(final EventData event) {
        return mayRfMap.get(event.getEvent());
    }

    public Collection<EventData> getMayCoSet(final EventData write) {
        final Event writeEvent = write.getEvent();
        final BigInteger writeAddress = (BigInteger) write.getAccessedAddress();
        final Set<Event> inSet = mayCoInMap.get(writeEvent);
        if (inSet == null) {
            return Collections.emptySet();
        }
        final EventData addressInit = trace.getAddressInits().get(writeAddress);
        if (addressInit == null) {
            return Collections.emptySet();
        }
        final Set<Event> outSet = mayCoOutMap.get(writeEvent);
        if (outSet == null) {
            EventData coPredecessor = null;
            EventData sameAddressWrite = addressInit;
            while (sameAddressWrite != null) {
                coPredecessor = sameAddressWrite;
                sameAddressWrite = sameAddressWrite.getCoSuccessor();
            }
            return Collections.singleton(coPredecessor);
        }
        return getMayCoSet(inSet, outSet, addressInit);
    }

    private Collection<EventData> getMayCoSet(final Set<Event> inSet, final Set<Event> outSet, final EventData addressInit) {
        final List<EventData> mayCoSet = new ArrayList<>();
        EventData sameAddressWrite = addressInit;
        while (sameAddressWrite != null) {
            final Event sameAddressEvent = sameAddressWrite.getEvent();
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
        final List<Init> inits = getProgram().getThreadEvents(Init.class);
        for (final Init init : inits) {
            final Thread initThread = init.getThread();
            relevantThreads.remove(initThread);
            for (final Event initThreadEvent : initThread.getEvents()) {
                final EventData data = new EventData(initThreadEvent);
                data.setId(trace.size());
                if (initThreadEvent == init) {
                    data.setAccessedAddress(exprEvaluator.evaluate(init.getAddress()));
                    data.setValue(exprEvaluator.evaluateToLiteral(init.getMemValue()));
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
    }

    public EventData initialize(final Event revisitRead, final Event revisitWrite, final Event lastReadInReadThread, final Event lastReadInWriteThread,
                                final boolean needsNewConsistencyCheck) {
        final EventData readEvent = trace.getData(revisitRead).orElseThrow(() ->
                new IllegalArgumentException("Backward revisit model does not contain revisit read " + revisitRead.getGlobalId() + ": " + revisitRead));
        final EventData writeEvent = trace.getData(revisitWrite).orElseThrow(() ->
                new IllegalArgumentException("Backward revisit model does not contain revisit write " + revisitWrite.getGlobalId() + ": " + revisitWrite));
        porfPrefix = lastReadInReadThread == null ? new HashMap<>(optimalPorfPrefixCapacity) : getPorfPrefix(lastReadInReadThread, false);
        setRf(readEvent, writeEvent);
        porfPrefix = lastReadInWriteThread == null ? new HashMap<>(optimalPorfPrefixCapacity) : getPorfPrefix(lastReadInWriteThread, false);
        setTrace();
        initFirstThreadEvent();
        initializeFields(needsNewConsistencyCheck, false);
        return writeEvent;
    }

    //========================== Public methods =========================

    public void setJump(final EventData jump, final boolean jumpIsExecuted) throws SolverException, InterruptedException, InvalidConfigurationException {
        CondJump jumpEvent = (CondJump) jump.getEvent();
        exprEvaluator.addNonDetCondition(jumpEvent, jumpEvent.getGuard(), jumpIsExecuted);
        needsNewConsistencyCheck = jump.wasExecuted() != jumpIsExecuted;
        jump.setWasExecuted(jumpIsExecuted);
        isConsistent = exprEvaluator.evaluateReachability(jumpIsExecuted ? jumpEvent.getGuard() : expressions.makeNot(jumpEvent.getGuard()));
    }

    public void setRf(final EventData read, final EventData write) {
        trace.setReadFrom(read, write, read.getReadFrom());
        final Event readEvent = read.getEvent();
        final Event writeEvent = write.getEvent();
        if (!(writeEvent instanceof Init)) {
            final Thread readThread = readEvent.getThread();
            final Thread writeThread = writeEvent.getThread();
            if (writeThread != readThread) {
                final Integer porfPrefixInWriteThread = porfPrefix.get(writeThread);
                final int writeGid = writeEvent.getGlobalId();
                if (porfPrefixInWriteThread == null || writeGid > porfPrefixInWriteThread) {
                    porfPrefix.put(writeThread, writeGid);
                    final Event lastReadInWriteThread = getLastRead(trace.getThreadEventsMap().get(writeThread), write.getLocalId() - 1);
                    if (lastReadInWriteThread != null) {
                        for (final Map.Entry<Thread, Integer> writePorfPrefixEntry : getPorfPrefix(lastReadInWriteThread, true).entrySet()) {
                            final Thread thread = writePorfPrefixEntry.getKey();
                            if (thread != readThread) {
                                final int writePorfPrefixBound = writePorfPrefixEntry.getValue();
                                final Integer porfPrefixBound = porfPrefix.get(thread);
                                if (porfPrefixBound == null || writePorfPrefixBound > porfPrefixBound) {
                                    porfPrefix.put(thread, writePorfPrefixBound);
                                }
                            }
                        }
                    }
                }
            }
        }
        porfPrefixes.computeIfAbsent(readEvent, e -> new ArrayList<>()).add(new HashMap<>(porfPrefix));
        addedEvents.add(read);
        final List<EventData> mayRfSet = mayRfMap.get(readEvent);
        if ((mayRfSet == null || mayRfSet.size() > 1) && write.getCoSuccessor() != null) {
            needsNewConsistencyCheck = true;
        }
    }

    public void setCo(final EventData write, final EventData newWrite, final Collection<EventData> mayCoSet) {
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
                final Event newWriteEvent = newWrite.getEvent();
                final EventData rmwLoad;
                if (newWriteEvent instanceof final RMWStore rmwStore) {
                    rmwLoad = getDataFromRmwLoad(rmwStore);
                } else if (newWriteEvent instanceof final RMWStoreExclusive rmwStore) {
                    rmwLoad = getDataFromRmwLoad(rmwStore);
                } else return;
                if (rmwLoad.getReadFrom() == write) {
                    return;
                }
            }
            needsNewConsistencyCheck = true;
        }
    }

    public void rollback(final EventData rollbackPoint, final boolean needsNewConsistencyCheck, final boolean lastNeedsNewConsistencyCheck) {
        lastAdded = rollbackPoint;
        final Event rollBackEvent = rollbackPoint.getEvent();
        final Thread rollbackThread = rollBackEvent.getThread();
        currentThreadIndex = relevantThreads.indexOf(rollbackThread);
        backtrackTrace(rollbackPoint);
        if (rollbackPoint.getReadFrom() != null) {
            final List<Map<Thread, Integer>> porfPrefixesForRollbackEvent = porfPrefixes.get(rollBackEvent);
            porfPrefixesForRollbackEvent.remove(porfPrefixesForRollbackEvent.size() - 1);
            final Event lastRead = getLastRead(trace.getThreadEventsMap().get(rollbackThread), rollbackPoint.getLocalId() - 1);
            porfPrefix = lastRead == null ? new HashMap<>(optimalPorfPrefixCapacity) : getPorfPrefix(lastRead, false);
        }
        addedEvents.remove(rollbackPoint);
        setTrace();
        initializeFields(needsNewConsistencyCheck, lastNeedsNewConsistencyCheck);
        if (backtrackingPoint != null && rollbackPoint.getId() <= backtrackingPoint.getId()) {
            if (backtrackReasonIsRel || rollbackPoint != backtrackingPoint) {
                backtrackingPoint = null;
            }
        }
    }

    public void rollbackCo(final EventData coPredecessor, final EventData rollbackPoint, final Event lastRead, final boolean forBackwardRevisit,
                           final boolean needsNewConsistencyCheck, final boolean lastNeedsNewConsistencyCheck) {
        final Thread currentThread = trace.getLast().getThread();
        final Thread rollbackThread = rollbackPoint.getThread();
        backtrackTrace(rollbackPoint);
        coPredecessor.setCoSuccessor(rollbackPoint.getCoSuccessor());
        if (coPredecessor.getCoSuccessor() != null) {
            coPredecessor.getCoSuccessor().setCoPredecessor(coPredecessor);
        }
        rollbackPoint.setCoPredecessor(null);
        rollbackPoint.setCoSuccessor(null);
        addedEvents.remove(rollbackPoint);
        setTrace();
        if (forBackwardRevisit) {
            initFirstThreadEvent();
        } else {
            lastAdded = rollbackPoint;
            currentThreadIndex = relevantThreads.indexOf(rollbackThread);
        }
        initializeFields(needsNewConsistencyCheck, lastNeedsNewConsistencyCheck);
        if (backtrackingPoint != null && rollbackPoint.getId() <= backtrackingPoint.getId()) {
            if (backtrackReasonIsRel || rollbackPoint != backtrackingPoint) {
                backtrackingPoint = null;
            }
        }
        if (currentThread != rollbackThread) {
            porfPrefix = lastRead == null ? new HashMap<>(optimalPorfPrefixCapacity) : getPorfPrefix(lastRead, false);
        }
    }

    public static Event getLastRead(final List<EventData> threadEvents, final int maxLocalId) {
        for (int i = maxLocalId; i >= 0; --i) {
            final EventData event = threadEvents.get(i);
            if (event.getReadFrom() != null) {
                return event.getEvent();
            }
        }
        return null;
    }

    public void resetConsistencyStatus() {
        isConsistent = true;
    }

    public boolean checkConsistency(final boolean ignoreLast) {
        if (reasoningMethod.usesReasoning()) {
            if (backtrackingPoint != null) {
                return false;
            }
        }
        if ((!needsNewConsistencyCheck && !ignoreLast) || (!lastNeedsNewConsistencyCheck && ignoreLast)) {
            return isConsistent;
        }
        final EventData ignoredEvent;
        if (ignoreLast) {
            ignoredEvent = trace.removeLast();
            if (reasoningMethod.usesReasoning()) {
                addedEvents.remove(ignoredEvent);
            }
        } else {
            ignoredEvent = null;
        }
        if (!underApproximateWmm) {
            isConsistent &= checkFullConsistency();
        } else {
            isConsistent &= !reasonKnown() && !hasPattern();
        }
        addedEvents.clear();
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
        consistencyCheckTimer.start();
        if (reasoningMethod.usesPatterns()) {
            solver.updateRemainingBasePredicates(trace);
        }
        final GenMCWMMSolver.Result result = solver.check(trace, reasoningMethod);
        consistencyCheckTimer.stop();
        if (result.getStatus() == CAATSolver.Status.CONSISTENT) {
            return true;
        } else if (result.getStatus() == CAATSolver.Status.INCONSISTENT) {
            isConsistent = false;
            addReasons(result.getCoreReasons());
            Map.Entry<EventData, Boolean> backtrack = result.getBacktrackingPoint();
            if (backtrack != null) {
                backtrackingPoint = backtrack.getKey();
                backtrackReasonIsRel = backtrack.getValue();
            }
            return false;
        } else {
            throw new IllegalArgumentException("Inconclusive");
        }
    }

    public boolean checkProperties() throws InterruptedException, SolverException, InvalidConfigurationException {
        final BooleanFormula properties = evalContext.getProperties();
        if (properties == null) {
            logger.debug("No global properties to check");
            return true;
        }
        final GenMCTraceEncoder traceEncoder = evalContext.getTraceEncoder();
        final BooleanFormulaManager bmgr = traceEncoder.getEncodingContext().getBooleanFormulaManager();
        return !checkProperties(bmgr.and(properties, traceEncoder.getPropertyRelevantEncoding()));
    }

    public void updateBacktrackingPoint(final GenMCExecutionModel backwardRevisitModel, final Event revisitWrite) {
        if (backtrackingPoint == null) {
            final EventData backwardRevisitBacktrackingPoint = backwardRevisitModel.backtrackingPoint;
            if (backwardRevisitBacktrackingPoint != null) {
                final Event backwardRevisitBacktrackingPointEvent = backwardRevisitBacktrackingPoint.getEvent();
                final boolean backwardRevisitBacktrackReasonIsRel = backwardRevisitModel.backtrackReasonIsRel;
                if (!(backwardRevisitBacktrackReasonIsRel && backwardRevisitBacktrackingPointEvent == revisitWrite)) {
                    backtrackingPoint = trace.getData(backwardRevisitBacktrackingPointEvent).orElse(null);
                    backtrackReasonIsRel = backwardRevisitBacktrackReasonIsRel;
                }
            }
        }
    }

    public void removeRevisitFromPorfPrefixes(final Event revisitRead) {
        final List<Map<Thread, Integer>> porfPrefixesForRevisitRead = porfPrefixes.get(revisitRead);
        porfPrefixesForRevisitRead.remove(porfPrefixesForRevisitRead.size() - 1);
    }

    public EventData nextEvent() throws InterruptedException, SolverException, InvalidConfigurationException {
        final EventData startEvent;
        boolean needsInit;
        if (lastAdded != null) {
            startEvent = lastAdded;
            needsInit = false;
        } else {
            startEvent = null;
            needsInit = true;
        }
        boolean newThread = false;
        while (currentThreadIndex < relevantThreads.size()) {
            final Thread thread = relevantThreads.get(currentThreadIndex);
            if (newThread) {
                final Event boundEvent = boundEvents.get(thread);
                porfPrefix = boundEvent == null ? new HashMap<>(optimalPorfPrefixCapacity) : getPorfPrefix(boundEvent, false);
            }
            Event e;
            if (needsInit) {
                final Optional<EventData> eventData = trace.getLastAddedThreadEventData(thread);
                if (eventData.isPresent()) {
                    e = eventData.get().getEvent().getSuccessor();
                } else {
                    e = thread.getEntry();
                }
            } else {
                e = startEvent.getEvent();
                if (e instanceof final CondJump jump && startEvent.wasExecuted()) {
                    e = jump.getLabel();
                } else {
                    e = e.getSuccessor();
                }
            }
            needsInit = true;
            while (e != null) {
                final EventData event = addEvent(e);
                lastAdded = event;
                e = lastAdded.getEvent();
                if (e instanceof MemoryEvent || (needsJumpRevisit && e instanceof CondJump)) {
                    return event;
                }
                if (e instanceof final CondJump jump && lastAdded.wasExecuted()) {
                    e = jump.getLabel();
                } else {
                    e = e.getSuccessor();
                }
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

    private void initializeFields(final boolean needsNewConsistencyCheck, final boolean lastNeedsNewConsistencyCheck) {
        isConsistent = true;
        this.needsNewConsistencyCheck = needsNewConsistencyCheck;
        this.lastNeedsNewConsistencyCheck = lastNeedsNewConsistencyCheck;
    }

    private void initFirstThreadEvent() {
        lastAdded = trace.getLastAddedThreadEventData(relevantThreads.get(0)).orElse(null);
        currentThreadIndex = 0;
    }

    private EventData addEvent(final Event e) throws InterruptedException, SolverException, InvalidConfigurationException {
        needsJumpRevisit = false;
        final EventData data = new EventData(e);
        data.setId(trace.size());

        data.setWasExecuted(true);
        if (e instanceof MemoryEvent) {
            // ===== Memory Events =====
            if (e instanceof Store store) {
                data.setValue(exprEvaluator.evaluateToLiteral(store.getMemValue()));
            }
            final BigInteger address = exprEvaluator.evaluateCurrentAddress(data);
            if (address == null || BigInteger.ZERO.equals(address)) {
                isConsistent = false;
                trace.addEvent(data);
                return data;
            }
            data.setAccessedAddress(address);
            final List<EventData> mayRfSet;
            if (e.getPredecessor() instanceof final ThreadStart start && start.isSpawned()) {
                mayRfSet = new ArrayList<>();
                final Thread currentThread = e.getThread();
                final Optional<EventData> threadStartStore = getThreadStartStore(currentThread);
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
        } else if (e instanceof final CondJump jump) {
            // ===== Jumps =====
            // We override the meaning of execution here. A jump is executed IFF its condition was true.
            if (jump.getGuard() instanceof final BoolLiteral bool) {
                data.setWasExecuted(bool.getValue());
            } else {
                final BigInteger guardResult = exprEvaluator.evaluate(jump.getGuard());
                if (guardResult == null) {
                    if (!checkConsistency(false)) {
                        isConsistent = false;
                        return data;
                    }
                    trace.addEvent(data);
                    lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
                    final BigInteger result = exprEvaluator.evaluateNonDet(jump.getGuard());
                    if (result == null) {
                        isConsistent = false;
                        return data;
                    }
                    final boolean wasExecuted = !BigInteger.ZERO.equals(result);
                    logger.debug("Exploring non-deterministic jump {}", jump);
                    data.setWasExecuted(wasExecuted);
                    exprEvaluator.addNonDetCondition(jump, jump.getGuard(), wasExecuted);
                    needsJumpRevisit = true;
                } else {
                    data.setWasExecuted(!BigInteger.ZERO.equals(guardResult));
                }
            }
        } else if (e instanceof final Assume assumptionEvent) {
            // ===== Assumptions =====
            final Event lastEvent = e.getPredecessor();
            if (!(lastEvent != null && lastEvent.getPredecessor() instanceof final ThreadStart start && start.isSpawned())) {
                trace.addEvent(data);
                lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
                final Expression assumption = assumptionEvent.getExpr();
                if (assumption instanceof final BoolLiteral bool) {
                    isConsistent = bool.getValue();
                } else {
                    isConsistent = exprEvaluator.evaluateReachability(assumption);
                    if (e.getSuccessor() instanceof ThreadJoin) {
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
        } else if (e instanceof final Assert assertionEvent) {
            // ===== Assertions =====
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
            if (assertionEvent.getExpression() instanceof final BoolLiteral bool) {
                final boolean failure = !bool.getValue();
                if (failure && checkFullConsistency()) {
                    hasError = true;
                }
            } else {
                hasError = checkProperties(assertionEvent);
            }
        } else if (e instanceof final Local assign) {
            // ===== Locals =====
            data.setValue(exprEvaluator.evaluateToLiteral(assign.getExpr()));
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        } else if (e instanceof final ThreadArgument arg) {
            // ===== Thread arguments =====
            final ThreadCreate creator = arg.getCreator();
            final Expression argExpr = creator.getArguments().get(arg.getIndex());
            final EventData creatorEvent = trace.getData(creator).orElseThrow();
            data.setValue(exprEvaluator.evaluateToLiteral(argExpr, creatorEvent));
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        } else if (e instanceof final Alloc alloc) {
            // ===== Allocations =====
            data.setValue(exprEvaluator.evaluateToLiteral(alloc.getAllocatedObject().getAddress()));
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        } else if (e instanceof final ThreadJoin join) {
            // ===== Thread joins =====
            final ThreadReturn threadReturn = (ThreadReturn) trace.getThreadReturn(join.getJoinThread()).getEvent();
            final Expression returnValue = threadReturn.getValue().orElseThrow(() ->
                    new IllegalStateException("Return value of thread " + threadReturn.getThread().getName() + " is referenced but does not return anything"));
            data.setValue(exprEvaluator.evaluateToLiteral(returnValue));
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        } else if (!(e instanceof StringAnnotation) && !(e instanceof ThreadStart) && !(e instanceof Label)) {
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        }
        return data;
    }

    private List<EventData> mayRfSet(final EventData target, final BigInteger address) {
        final Map<Event, Event[]> mayRfMap;
        final Event targetEvent = target.getEvent();
        final boolean isWrite = targetEvent instanceof Store;
        if (isWrite) {
            mayRfMap = mayRfOutMap;
        } else {
            mayRfMap = mayRfInMap;
        }
        final Event[] mayRfSet = mayRfMap.get(targetEvent);
        if (mayRfSet == null) {
            return Collections.emptyList();
        }
        final List<EventData> mayRfSetData = new ArrayList<>(mayRfSet.length);
        for (final Event event : mayRfSet) {
            final Optional<EventData> e = trace.getData(event);
            if (e.isPresent()) {
                final EventData eventData = e.get();
                if (address.equals(eventData.getAccessedAddress())) {
                    mayRfSetData.add(eventData);
                }
            }
        }
        if (!isWrite) {
            applyExplorationOrder(mayRfSetData);
        }
        return mayRfSetData;
    }

    private void applyExplorationOrder(final List<EventData> maySet) {
        if (explorationOrder != null) {
            maySet.sort(explorationOrder);
        }
    }

    private Optional<EventData> getThreadStartStore(final Thread thread) {
        final Optional<EventData> threadCreator = trace.getData(thread.getEntry().getCreator());
        return threadCreator.map(creator -> trace.getObjectById(trace.getId(creator) + 1));
    }

    private EventData endThread(final Thread thread) {
        final EventData threadExit = new EventData(thread.getExit());
        threadExit.setId(trace.size());
        trace.addEvent(threadExit);
        lastAdded = threadExit;
        lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        return threadExit;
    }

    private void checkForExclusiveLoad(final EventData event) {
        if (event.getEvent() instanceof final RMWStoreExclusive exStore) {
            trace.addLxsxPair(event, getDataFromRmwLoad(exStore));
        }
    }

    private EventData getDataFromRmwLoad(final RMWStore rmwStore) {
        return trace.getData(rmwStore.getLoadEvent())
                .orElseThrow(() -> new IllegalArgumentException("RMW load for event " + rmwStore.getGlobalId() + ": " + rmwStore + " is not in trace yet"));
    }

    private EventData getDataFromRmwLoad(final RMWStoreExclusive rmwStore) {
        return trace.getData(mayLxsxMap.get(rmwStore))
                .orElseThrow(() -> new IllegalArgumentException("Exclusive load for event " + rmwStore.getGlobalId() + ": " + rmwStore + " is not in trace yet"));
    }

    private void backtrackTrace(final EventData backtrackingPoint) {
        final List<EventData> removedEvents = trace.rollback(backtrackingPoint);
        if (reasoningMethod.usesReasoning()) {
            addedEvents.removeAll(removedEvents);
        }
        removedEvents.clear();
    }

    private Map<Thread, Integer> getPorfPrefix(final Event event, final boolean readOnly) {
        final List<Map<Thread, Integer>> porfPrefixesForEvent = porfPrefixes.get(event);
        final Map<Thread, Integer> porfPrefixForEvent = porfPrefixesForEvent.get(porfPrefixesForEvent.size() - 1);
        return readOnly ? porfPrefixForEvent : new HashMap<>(porfPrefixForEvent);
    }

    private boolean reasonKnown() {
        if (reasoningMethod.usesReasons()) {
            reasonEvaluationTimer.start();
            EVENT_LOOP:
            for (final EventData event : addedEvents) {
                final List<GenMCReason> reasonsForEvent = reasons.get(event.getEvent());
                if (reasonsForEvent != null) {
                    for (final GenMCReason reason : reasonsForEvent) {
                        boolean reasonKnown = true;
                        for (final CoreLiteral coreLiteral : reason.getLiteralsToIterateOver()) {
                            if (!evaluate(coreLiteral, event)) {
                                reasonKnown = false;
                                break;
                            }
                        }
                        if (reasonKnown) {
                            int backtrackingPointId = -1;
                            for (final CoreLiteral lit : reason.getLiteralsToIterateOver()) {
                                for (final Event litEvent : getEvents(lit)) {
                                    final EventData eventData = trace.getData(litEvent).orElseThrow(() ->
                                            new IllegalStateException("Event " + litEvent + " is not in trace yet but occurs in reason cube " + reason + " that is fulfilled"));
                                    final int eventId = eventData.getId();
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
            reasonEvaluationTimer.stop();
            return backtrackingPoint != null;
        }
        return false;
    }

    private boolean evaluate(final CoreLiteral literal, final EventData event) {
        if (literal instanceof final RelLiteral lit) {
            final Relation relation = lit.getRelation();
            if (relation == rf) {
                return evaluateRfLiteral(lit, event);
            } else if (relation == co) {
                return evaluateCoLiteral(lit, event);
            } else if (relation == lxsx) {
                return evaluateLxsxLiteral(lit, event);
            } else {
                throw new IllegalArgumentException("Expected base relation for RelLiteral and not " + relation);
            }
        } else if (literal instanceof final ExecLiteral lit) {
            return evaluateExecLiteral(lit, event);
        } else if (literal instanceof final AddressLiteral loc) {
            return evaluateAddressLiteral(loc, event);
        } else {
            throw new IllegalArgumentException("CoreLiteral " + literal + " is not supported");
        }
    }

    private boolean evaluateExecLiteral(final ExecLiteral exec, final EventData event) {
        final Event targetEvent = exec.getEvent();
        if (event != null && event.getEvent() == targetEvent) {
            return exec.isPositive();
        }
        final boolean execValue = trace.getData(targetEvent).isPresent();
        return exec.isNegative() != execValue;
    }

    private boolean evaluateAddressLiteral(final AddressLiteral address, final EventData event) {
        final Event firstEvent = address.getFirst();
        final Event secondEvent = address.getSecond();
        final boolean addressValue;
        final Event e;
        if (event != null && ((e = event.getEvent()) == firstEvent || e == secondEvent)) {
            if (e == firstEvent) {
                final Optional<EventData> second = trace.getData(address.getSecond());
                addressValue = second.filter(eventData -> event.getAccessedAddress().equals(eventData.getAccessedAddress())).isPresent();
            } else {
                final Optional<EventData> first = trace.getData(address.getFirst());
                addressValue = first.map(eventData -> eventData.getAccessedAddress().equals(event.getAccessedAddress())).orElse(false);
            }
        } else {
            final Optional<EventData> first = trace.getData(firstEvent);
            final Optional<EventData> second = trace.getData(secondEvent);
            if (first.isEmpty() || second.isEmpty()) {
                addressValue = false;
            } else {
                addressValue = first.get().getAccessedAddress().equals(second.get().getAccessedAddress());
            }
        }
        return address.isNegative() != addressValue;
    }

    private boolean evaluateCoLiteral(final RelLiteral co, final EventData event) {
        final Event target = co.getTarget();
        if (event != null && event.getEvent() == target) {
            EventData cur = event;
            while (cur != null) {
                if (cur.getEvent() == co.getSource()) {
                    return co.isPositive();
                }
                cur = cur.getCoPredecessor();
            }
        } else {
            final Optional<EventData> suc = trace.getData(target);
            if (suc.isPresent()) {
                EventData cur = suc.get();
                while (cur != null) {
                    if (cur.getEvent() == co.getSource()) {
                        return co.isPositive();
                    }
                    cur = cur.getCoPredecessor();
                }
            }
        }
        return co.isNegative();
    }

    private boolean evaluateRfLiteral(final RelLiteral rf, final EventData event) {
        final Event target = rf.getTarget();
        final boolean rfValue;
        if (event != null && event.getEvent() == target) {
            final EventData readFrom = event.getReadFrom();
            rfValue = readFrom != null && readFrom.getEvent() == rf.getSource();
        } else {
            final Optional<EventData> read = trace.getData(rf.getTarget());
            if (read.isPresent()) {
                final EventData readFrom = read.get().getReadFrom();
                rfValue = readFrom != null && readFrom.getEvent() == rf.getSource();
            } else {
                rfValue = false;
            }
        }
        return rf.isNegative() != rfValue;
    }

    private boolean evaluateLxsxLiteral(final RelLiteral lxsx, final EventData event) {
        final boolean lxsxValue;
        final Event target = lxsx.getTarget();
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
            patternEvaluationTimer.start();
            solver.updateBasePredicates(trace);
            Map.Entry<EventData, Boolean> backtrack = evaluatePatterns();
            patternEvaluationTimer.stop();
            if (backtrack != null) {
                backtrackingPoint = backtrack.getKey();
                backtrackReasonIsRel = backtrack.getValue();
                return true;
            }
        }
        return false;
    }

    private Map.Entry<EventData, Boolean> evaluatePatterns() {
        Map.Entry<EventData, Boolean> backtrack;
        for (final EventData event : addedEvents) {
            final EventData readFrom = event.getReadFrom();
            if (readFrom != null) {
                if ((backtrack = patterns.isViolated(readFrom, event, rf)) != null) {
                    return backtrack;
                }
            } else {
                if (event.getEvent() instanceof RMWStoreExclusive) {
                    if ((backtrack = patterns.isViolated(trace.getLxsxPairs().get(event), event, lxsx)) != null) {
                        return backtrack;
                    }
                }
                final List<Integer> alreadyCheckedIds = new ArrayList<>();
                final EventData coPredecessor = event.getCoPredecessor();
                final EventData coSuccessor = event.getCoSuccessor();
                if (coPredecessor != null || coSuccessor != null) {
                    final int eventId = event.getId();
                    alreadyCheckedIds.add(eventId);
                    if (coPredecessor != null) {
                        for (final EventData anyCoPredecessor : coPredecessor.getCoPredecessors()) {
                            if (!alreadyCheckedIds.contains(anyCoPredecessor.getId())) {
                                if ((backtrack = patterns.isViolated(anyCoPredecessor, event, co)) != null) {
                                    return backtrack;
                                }
                            }
                        }
                    }
                    if (coSuccessor != null) {
                        for (final EventData anyCoSuccessor : coSuccessor.getCoSuccessors()) {
                            if (!alreadyCheckedIds.contains(anyCoSuccessor.getId())) {
                                if ((backtrack = patterns.isViolated(event, anyCoSuccessor, co)) != null) {
                                    return backtrack;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void addReasons(final DNF<CoreLiteral> newReasons) {
        final Map<Thread, Event> maxEventForThreads = new HashMap<>();
        int backtrackingPointId = Integer.MAX_VALUE;
        for (final Conjunction<CoreLiteral> reason : newReasons.getCubes()) {
            final GenMCReason cube = (GenMCReason) reason;
            EventData cubeBacktrackingPoint = null;
            int cubeBacktrackingPointId = -1;
            boolean cubeBacktrackingPointReasonIsRel = true;
            for (final CoreLiteral lit : cube.getLiterals()) {
                if (lit instanceof final RelLiteral relLit && relLit.getRelation() == rf) {
                    final Event read = relLit.getTarget();
                    final Thread eventThread = read.getThread();
                    final Event maxEventForThread = maxEventForThreads.get(eventThread);
                    if (maxEventForThread == null || maxEventForThread.getGlobalId() < read.getGlobalId()) {
                        maxEventForThreads.put(eventThread, read);
                    }
                    final EventData readData = trace.getData(read).orElse(null);
                    final EventData writeData = trace.getData(relLit.getSource()).orElse(null);
                    if (readData != null && writeData != null) {
                        final EventData maxEvent = readData.getId() > writeData.getId() ? readData : writeData;
                        final int eventId = maxEvent.getId();
                        if (eventId >= cubeBacktrackingPointId) {
                            cubeBacktrackingPoint = maxEvent;
                            cubeBacktrackingPointId = eventId;
                            cubeBacktrackingPointReasonIsRel = true;
                        }
                    }
                } else {
                    for (final Event event : getEvents(lit)) {
                        if (event instanceof Init) {
                            continue;
                        }
                        final Thread eventThread = event.getThread();
                        final Event maxEventForThread = maxEventForThreads.get(eventThread);
                        if (maxEventForThread == null || maxEventForThread.getGlobalId() < event.getGlobalId()) {
                            maxEventForThreads.put(eventThread, event);
                            final EventData eventData = trace.getData(event).orElse(null);
                            if (eventData != null) {
                                final int eventId = eventData.getId();
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
            for (final Event event : maxEventForThreads.values()) {
                reasons.computeIfAbsent(event, e -> new ArrayList<>()).add(cube);
            }
            maxEventForThreads.clear();
        }
    }

    private static Collection<Event> getEvents(final CoreLiteral lit) {
        if (lit instanceof final RelLiteral relLit) {
            return List.of(relLit.getTarget(), relLit.getSource());
        }
        if (lit instanceof final ExecLiteral execLit) {
            return Collections.singleton(execLit.getEvent());
        }
        if (lit instanceof final AddressLiteral addrLit) {
            return List.of(addrLit.getFirst(), addrLit.getSecond());
        }
        throw new IllegalArgumentException("CoreLiteral " + lit + " is not supported");
    }

    @FunctionalInterface
    private interface PropertyChecker<T> {
        boolean check(final T properties) throws InterruptedException, SolverException, InvalidConfigurationException;
    }

    private boolean checkProperties(final Assert assertion) throws InterruptedException, SolverException, InvalidConfigurationException {
        return checkProperties(expressions.makeNot(assertion.getExpression()), exprEvaluator::evaluateReachability);
    }

    private boolean checkProperties(final BooleanFormula properties) throws InterruptedException, SolverException, InvalidConfigurationException {
        return checkProperties(properties, exprEvaluator::evaluateReachability);
    }

    private <T> boolean checkProperties(final T properties, final PropertyChecker<T> propertyChecker) throws InterruptedException, SolverException, InvalidConfigurationException {
        logger.debug("Checking properties");
        if (!propertyChecker.check(properties)) {
            return false;
        }
        return checkFullConsistency();
    }

    private void setTrace() {
        exprEvaluator.setTrace(trace);
    }
}
