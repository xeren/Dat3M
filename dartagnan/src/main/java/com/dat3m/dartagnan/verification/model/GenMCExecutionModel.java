package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.configuration.GenMCReasoning;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.Type;
import com.dat3m.dartagnan.expression.base.ExpressionBase;
import com.dat3m.dartagnan.expression.booleans.*;
import com.dat3m.dartagnan.expression.floats.FloatCmpExpr;
import com.dat3m.dartagnan.expression.integers.IntCmpExpr;
import com.dat3m.dartagnan.expression.misc.ITEExpr;
import com.dat3m.dartagnan.expression.processing.ExprSimplifier;
import com.dat3m.dartagnan.expression.processing.ExprTransformer;
import com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.expression.type.IntegerType;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.annotations.StringAnnotation;
import com.dat3m.dartagnan.program.event.core.threading.*;
import com.dat3m.dartagnan.program.misc.NonDetValue;
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
import static com.dat3m.dartagnan.verification.solving.GenMCSolver.inPorfPrefix;
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
    private static final ExprSimplifier simplifier = new ExprSimplifier(true);

    private static int consistencyChecks;
    private static DNF<CoreLiteral> allReasons;

    private static final Timer consistencyCheckTimer = new Timer();
    private static final Timer assertionReasonEvaluationTimer = new Timer();
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
    private static final Map<Assert, DNF<CoreLiteral>> assertionReasons = new HashMap<>();

    // ============= Model specific  =============
    private EventData lastAdded = null;
    private int currentThreadIndex = 0;
    private boolean hasError = false;
    private boolean isConsistent = true;
    private boolean needsNewConsistencyCheck = true;
    private boolean lastNeedsNewConsistencyCheck = true;
    private boolean needsJumpRevisit = false;
    private boolean initiallyPropagated = false;
    private boolean initiallyPropagatedByPatterns = false;
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
    private final boolean assertionGuidance;

    private final Map<Thread, Event> boundEvents;
    private final Set<Event> failedJoinAssumes;
    private final Map<Event, List<EventData>> mayRfMap;
    private final List<EventData> addedEvents;
    private final List<EventData> addedReasoningEvents;

    private GenMCExecutionModel(final GenMCEventDomain trace, final VerificationTask task, final GenMCWMMSolver solver, final ExprEvaluationContext evalContext,
                                final ExpressionEvaluator exprEvaluator, final Map<Thread, Event> boundEvents, final Set<Event> failedJoinAssumes,
                                final Relation rf, final Relation co, final Relation lxsx, final Comparator<EventData> explorationOrder,
                                final int optimalPorfPrefixCapacity, final GenMCReasoning reasoningMethod, final boolean underApproximateWmm,
                                final boolean assertionGuidance) {
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
        this.assertionGuidance = assertionGuidance;

        this.boundEvents = boundEvents;
        this.failedJoinAssumes = failedJoinAssumes;
        this.mayRfMap = new HashMap<>();
        this.addedEvents = new ArrayList<>();
        this.addedReasoningEvents = new ArrayList<>();
    }

    public static GenMCExecutionModel withTask(final VerificationTask task, final Context analysisContext, final ExprEvaluationContext evalContext,
                                               final RelationAnalysis.Knowledge rfKnowledge, final GenMCReasoning reasoningMethod, final boolean underApproximateWmm,
                                               final boolean assertionGuidance)
            throws InvalidConfigurationException {
        final Program program = task.getProgram();
        if (!program.getThreadEvents(ControlBarrier.class).isEmpty()) {
            throw new UnsupportedOperationException("ControlBarrier not yet implemented for GenMC");
        }

        consistencyChecks = 0;
        allReasons = DNF.FALSE();
        consistencyCheckTimer.reset();
        reasonEvaluationTimer.reset();
        patternEvaluationTimer.reset();
        reasons.clear();
        final Wmm memoryModel = task.getMemoryModel();
        Relation rf = memoryModel.getRelation(RF);
        Relation co = memoryModel.getRelation(CO);
        Relation lxsx = memoryModel.getRelation(LXSX);
        final RelationAnalysis relations = analysisContext.requires(RelationAnalysis.class);
        final EventGraph mayRfGraph = rfKnowledge.getMaySet();
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
        assertionReasons.clear();
        if (assertionGuidance) {
            for (final Assert assertion : task.getProgram().getThreadEvents(Assert.class)) {
                assertionReasons.put(assertion, DNF.FALSE());
            }
        }

        final GenMCEventDomain trace = new GenMCEventDomain(relevantThreads.size());
        final GenMCWMMSolver solver = GenMCWMMSolver.withTask(task, analysisContext, reasoningMethod);
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
                optimalPorfPrefixCapacity, reasoningMethod, underApproximateWmm, assertionGuidance);
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
                rf, co, lxsx, explorationOrder, optimalPorfPrefixCapacity, reasoningMethod, underApproximateWmm, assertionGuidance);
        if (reasoningMethod.usesReasoning()) {
            for (final EventData addedEvent : addedReasoningEvents) {
                model.trace.getData(addedEvent.getEvent()).ifPresent(model.addedReasoningEvents::add);
            }
        }
        initiallyPropagated = false;
        initiallyPropagatedByPatterns = false;
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

    public static Timer getAssertionReasonEvaluationTimer() {
        return assertionReasonEvaluationTimer;
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
        final Set<Event> inSet = mayCoInMap.get(writeEvent);
        if (inSet == null) {
            return Collections.emptySet();
        }
        final EventData addressInit = trace.getAddressInit(write.getAccessedAddress());
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
        if (reasoningMethod.usesReasoning()) {
            addedReasoningEvents.add(read);
        }
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
        if (reasoningMethod.usesReasoning()) {
            addedReasoningEvents.add(newWrite);
        }
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
        if (reasoningMethod.usesReasoning()) {
            addedReasoningEvents.remove(rollbackPoint);
        }
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
        if (reasoningMethod.usesReasoning()) {
            addedReasoningEvents.remove(rollbackPoint);
        }
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

    public boolean checkConsistency(final boolean ignoreLast) throws SolverException, InterruptedException, InvalidConfigurationException {
        if (reasoningMethod.usesReasoning()) {
            if (backtrackingPoint != null) {
                return false;
            }
        }
        Map.Entry<Boolean, Boolean> assertionViolationResult = null;
        if (assertionGuidance && (assertionViolationResult = assertionViolationImpossible(ignoreLast)).getKey()) {
            return false;
        }
        final boolean reasonsChecked = assertionViolationResult != null && assertionViolationResult.getValue();
        if ((!needsNewConsistencyCheck && !ignoreLast) || (!lastNeedsNewConsistencyCheck && ignoreLast)) {
            return isConsistent;
        }
        final EventData ignoredEvent;
        if (ignoreLast) {
            ignoredEvent = trace.removeLast();
        } else {
            ignoredEvent = null;
        }
        if (!underApproximateWmm) {
            isConsistent &= checkFullConsistency(reasonsChecked);
        } else {
            isConsistent &= (reasonsChecked || !reasonKnown()) && !hasPattern();
            addedEvents.clear();
            addedReasoningEvents.clear();
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
        }
        return isConsistent;
    }

    public boolean checkFullConsistency() {
        return checkFullConsistency(false);
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
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
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
        } else if (!(e instanceof StringAnnotation) && !(e instanceof ThreadStart)) {
            if (e instanceof GenericVisibleEvent) {
                addedEvents.add(data);
            }
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

    public void assertionReasoning() throws SolverException, InterruptedException, InvalidConfigurationException {
        logger.debug("Starting assertion reasoning");
        for (final Assert assertion : assertionReasons.keySet()) {
            final DNF<CoreLiteral> newReasons = startAssertionReasoning(assertion);
            assertionReasons.computeIfPresent(assertion, (a, reasons) ->
                    newReasons.isLessThan(reasons) || newReasons.equals(reasons) ? reasons : reasons.or(newReasons)
            );
        }
        logger.debug("Finished assertion reasoning. Current assertion reasons:");
        assertionReasons.forEach((assertion, reasons) -> logger.debug("{}: {} : {}", assertion.getGlobalId(), assertion, reasons));
    }

    private DNF<CoreLiteral> startAssertionReasoning(final Assert assertion) throws SolverException, InterruptedException, InvalidConfigurationException {
        DNF<CoreLiteral> assertionReasons = DNF.TRUE();
        try {
            if (trace.getData(assertion).isPresent()) {
                assertionReasons = reasoning(assertion.getPredecessor(), assertion.pre(expressions.makeTrue(), expressions), new HashMap<>(), assertion.getThread());
            } else {
                for (Map.Entry<Event, Expression> entryEvent : getAssertionEntryPoints(assertion).entrySet()) {
                    assertionReasons = assertionReasons.and(reasoning(entryEvent.getKey().getPredecessor(), entryEvent.getValue(), new HashMap<>(), assertion.getThread()),
                            false);
                }
            }
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof SolverException solverException) {
                    throw solverException;
                }
                if (cause instanceof InterruptedException interruptedException) {
                    throw interruptedException;
                }
                if (cause instanceof InvalidConfigurationException invalidConfigurationException) {
                    throw invalidConfigurationException;
                }
            }
            throw e;
        }
        return assertionReasons;
    }

    private Map<Event, Expression> getAssertionEntryPoints(final Assert assertion) {
        Event event = assertion;
        Expression condition = assertion.pre(expressions.makeTrue(), expressions);
        final Map<Event, Expression> entryPoints = new HashMap<>();
        while ((event = event.getPredecessor()) != null) {
            if (event instanceof final Load read && condition.getRegs().contains(read.getResultRegister())) {
                condition = expressions.makeFalse();
            } else if (event instanceof final Label label) {
                label.resetInitPost();
                if (!(condition instanceof final BoolLiteral bool && bool.getValue()) && trace.getData(label).isEmpty()) {
                    for (final Event labelUser : event.getUsers()) {
                        if (labelUser instanceof CondJump jumpToLabel && trace.getData(labelUser).isPresent()) {
                            entryPoints.put(labelUser, expressions.makeOr(expressions.makeNot(jumpToLabel.getGuard()), condition).accept(simplifier));
                        }
                    }
                }
            } else if (event instanceof final CondJump jump && !jump.isGoto() && !(condition instanceof final BoolLiteral bool && bool.getValue())) {
                final Optional<EventData> jumpExec;
                if ((jumpExec = trace.getData(jump)).isPresent() && jumpExec.get().wasExecuted() && trace.getData(jump.getSuccessor()).isEmpty()) {
                    entryPoints.put(jump, expressions.makeOr(jump.getGuard(), condition).accept(simplifier));
                }
            } else if (event instanceof final Assume joinAssume) {
                if (joinAssume.getSuccessor() instanceof ThreadJoin && trace.getData(joinAssume).isEmpty() && trace.getData(joinAssume.getPredecessor()).isPresent()) {
                    entryPoints.put(joinAssume, expressions.makeOr(expressions.makeNot(joinAssume.getExpr()), condition).accept(simplifier));
                }
            }
            condition = event.pre(condition, expressions).accept(simplifier);
        }
        return entryPoints;
    }

    private static final Comparator<Map.Entry<Integer, Boolean>> executionIdComparator =
            Comparator.<Map.Entry<Integer, Boolean>>comparingInt(Map.Entry::getKey).reversed();

    private DNF<CoreLiteral> reasoning(final Event event, final Expression condition, final Map<Thread, List<Map.Entry<Integer, Boolean>>> executionEncodedIds,
                                       final Thread exclusionThread) {
        if (event == null) {
            return DNF.TRUE();
        }
        if (condition instanceof BoolLiteral lit) {
            if (lit.getValue() && trace.getData(event).isPresent()) {
                final Thread eventThread = event.getThread();
                if (eventThread != exclusionThread) {
                    final List<Map.Entry<Integer, Boolean>> executionIds = executionEncodedIds.get(eventThread);
                    if (executionIds != null && !executionIds.isEmpty()) {
                        executionIds.get(executionIds.size() - 1).setValue(true);
                        return DNF.TRUE();
                    }
                }
            }
        }
        if (event instanceof final Load read) {
            final Set<Register> conditionRegs = condition.getRegs();
            final Register readReg = read.getResultRegister();
            if (conditionRegs.contains(readReg)) {
                boolean allStoresFulfill = true;
                for (final Event mayWrite : mayRfInMap.get(event)) {
                    final Store mayStore = (Store) mayWrite;
                    if (!(readReg.replaceIn(condition, mayStore.getMemValue()).accept(simplifier) instanceof final BoolLiteral bool && bool.getValue())) {
                        allStoresFulfill = false;
                        break;
                    }
                }
                if (allStoresFulfill) {
                    return reasoning(event.getPredecessor(), expressions.makeTrue(), executionEncodedIds, exclusionThread);
                }
                final Optional<EventData> traceRead = trace.getData(event);
                if (traceRead.isPresent()) {
                    final EventData loadEvent = traceRead.get();
                    final EventData storeEvent = loadEvent.getReadFrom();
                    final Store write = (Store) storeEvent.getEvent();
                    if (condition instanceof final IntCmpExpr cmpExpr) {
                        Register cmpReg = null;
                        if (cmpExpr.getLeft() == readReg && cmpExpr.getRight() instanceof final Register otherReg) {
                            cmpReg = otherReg;
                        } else if (cmpExpr.getRight() == readReg && cmpExpr.getLeft() instanceof final Register otherReg) {
                            cmpReg = otherReg;
                        }
                        if (cmpReg != null) {
                            final EventData lastRegWriter = trace.getLastRegWriter(cmpReg, loadEvent);
                            if (lastRegWriter.getEvent() instanceof final Load cmpLoad && (lastRegWriter.getReadFrom() == storeEvent)) {
                                final DNF<CoreLiteral> reason = new DNF<>(newRfLiteralIfNeeded(lastRegWriter.getReadFrom().getEvent(), cmpLoad)
                                        .and(newRfLiteralIfNeeded(write, event)));
                                if (reason.isTriviallyTrue()) {
                                    return reasoning(event.getPredecessor(), expressions.makeTrue(), executionEncodedIds, exclusionThread);
                                } else {
                                    return reason;
                                }
                            }
                        }
                    }
                    Expression newCondition = condition;
                    for (final Register reg : conditionRegs) {
                        if (reg != readReg) {
                            newCondition = reg.replaceIn(newCondition, evaluateRegister(reg, loadEvent));
                        }
                    }
                    if (mayRfInMap.get(event).length == 1) {
                        return reasoning(write, readReg.replaceIn(newCondition, write.getMemValue()), executionEncodedIds, exclusionThread)
                                .and(reasoning(event.getPredecessor(),
                                                readReg.replaceIn(condition, evaluateRegister(write.getMemValue(), storeEvent)).accept(simplifier), executionEncodedIds,
                                                exclusionThread),
                                        false);
                    } else {
                        addExecutionId(event, executionEncodedIds);
                        addExecutionId(write, executionEncodedIds);
                        final DNF<CoreLiteral> rfReasons = reasoning(write, readReg.replaceIn(newCondition, write.getMemValue()), executionEncodedIds, exclusionThread)
                                .and(reasoning(event.getPredecessor(),
                                                readReg.replaceIn(condition, evaluateRegister(write.getMemValue(), storeEvent)).accept(simplifier), executionEncodedIds,
                                                exclusionThread),
                                        false);
                        final boolean storeEncodesExecution = removeExecutionId(write, executionEncodedIds);
                        final boolean loadEncodesExecution = removeExecutionId(event, executionEncodedIds);
                        return rfReasons.and(new DNF<>(newRfLiteral(write, event, storeEncodesExecution || loadEncodesExecution)));
                    }
                } else {
                    return reasoning(event.getPredecessor(), expressions.makeFalse(), executionEncodedIds, exclusionThread);
                }
            } else {
                return reasoning(event.getPredecessor(), condition, executionEncodedIds, exclusionThread);
            }
        }
        if (event instanceof final Label label) {
            final DNF<CoreLiteral> result = reasoning(event.getPredecessor(), event.pre(condition, expressions), executionEncodedIds, exclusionThread);
            label.resetPost();
            return result;
        }
        if (event instanceof final CondJump jump) {
            final Label label = jump.getLabel();
            final boolean labelExecuted = label.getPost() != null;
            final Expression labelPost = labelExecuted ? label.getPost() : expressions.makeTrue();
            if (jump.isGoto() || labelPost.equals(condition)) {
                return reasoning(event.getPredecessor(), labelPost, executionEncodedIds, exclusionThread);
            }
            if (labelPost instanceof final BoolLiteral bool && bool.getValue()) {
                return reasoning(event.getPredecessor(), condition, executionEncodedIds, exclusionThread);
            }
            if (condition instanceof final BoolLiteral bool && bool.getValue()) {
                return reasoning(event.getPredecessor(), labelPost, executionEncodedIds, exclusionThread);
            }
            final List<Map.Entry<Integer, Boolean>> executionIds = executionEncodedIds.get(event.getThread());
            final Map.Entry<Integer, Boolean> lastExecutionId;
            if (executionIds != null && !executionIds.isEmpty()
                    && (lastExecutionId = executionIds.get(executionIds.size() - 1)).getKey() < label.getGlobalId()) {
                lastExecutionId.setValue(true);
                return reasoning(event.getPredecessor(), condition, executionEncodedIds, exclusionThread);
            }
            final Optional<EventData> jumpEvent = trace.getData(jump);
            if (jumpEvent.isPresent()) {
                if (jumpEvent.get().wasExecuted()) {
                    return reasoning(event.getPredecessor(), expressions.makeAnd(jump.getGuard(), labelPost).accept(simplifier), executionEncodedIds, exclusionThread);
                } else {
                    return reasoning(event.getPredecessor(), expressions.makeAnd(expressions.makeNot(jump.getGuard()), condition).accept(simplifier), executionEncodedIds,
                            exclusionThread);
                }
            } else {
                return reasoning(event.getPredecessor(), event.pre(condition, expressions).accept(simplifier), executionEncodedIds, exclusionThread);
            }
        }
        if (event instanceof Alloc alloc) {
            return reasoning(event.getPredecessor(), alloc.getResultRegister().replaceIn(condition, alloc.getAllocatedObject().getAddress()).accept(simplifier),
                    executionEncodedIds, exclusionThread);
        }
        if (event instanceof RegWriter || event instanceof Assume) {
            return reasoning(event.getPredecessor(), event.pre(condition, expressions).accept(simplifier), executionEncodedIds, exclusionThread);
        }
        return reasoning(event.getPredecessor(), condition, executionEncodedIds, exclusionThread);
    }

    private void addExecutionId(final Event event, final Map<Thread, List<Map.Entry<Integer, Boolean>>> executionIds) {
        List<Map.Entry<Integer, Boolean>> eventExecutionIds = executionIds.computeIfAbsent(event.getThread(), t -> new ArrayList<>());
        eventExecutionIds.add(new AbstractMap.SimpleEntry<>(event.getGlobalId(), false));
        eventExecutionIds.sort(executionIdComparator);
    }

    private boolean removeExecutionId(final Event event, final Map<Thread, List<Map.Entry<Integer, Boolean>>> executionIds) {
        final List<Map.Entry<Integer, Boolean>> eventExecutionIds = executionIds.get(event.getThread());
        final int eventGid = event.getGlobalId();
        boolean encodesExecution = false;
        Map.Entry<Integer, Boolean> eventExecutionId = null;
        for (final Map.Entry<Integer, Boolean> entry : eventExecutionIds) {
            if (entry.getKey() == eventGid) {
                encodesExecution = entry.getValue();
                eventExecutionId = entry;
                break;
            }
        }
        eventExecutionIds.remove(eventExecutionId);
        return encodesExecution;
    }

    private Expression evaluateRegister(final Expression expr, final EventData event) {
        final BigInteger value = evaluateSafely(expr, event);
        final Expression evalExpr;
        if (value != null) {
            final Type regType = expr.getType();
            if (regType instanceof BooleanType) {
                evalExpr = expressions.makeValue(BigInteger.ONE.equals(value));
            } else if (regType instanceof final IntegerType intType) {
                evalExpr = expressions.makeValue(value, intType);
            } else {
                throw new IllegalStateException();
            }
        } else {
            throw new UnsupportedOperationException("Non-deterministic values for assertion guidance are not yet fully supported");
        }
        return evalExpr;
    }

    private BigInteger evaluateSafely(final Expression expr, final EventData event) {
        try {
            return exprEvaluator.evaluate(expr, event);
        } catch (SolverException | InterruptedException | InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private class RfLiteral extends RelLiteral {
        private final boolean encodesExecution;

        private RfLiteral(final Event e1, final Event e2, final boolean encodesExecution) {
            super(rf, e1, e2, true);
            this.encodesExecution = encodesExecution;
        }
    }

    private RfLiteral newRfLiteral(final Event e1, final Event e2, final boolean encodesExecution) {
        return new RfLiteral(e1, e2, encodesExecution);
    }

    private Conjunction<CoreLiteral> newRfLiteralIfNeeded(final Event e1, final Event e2) {
        return mayRfInMap.get(e2).length == 1 ? Conjunction.TRUE() : new Conjunction<>(new RfLiteral(e1, e2, true));
    }

    private void backtrackTrace(final EventData backtrackingPoint) {
        final List<EventData> removedEvents = trace.rollback(backtrackingPoint);
        addedEvents.removeAll(removedEvents);
        if (reasoningMethod.usesReasoning()) {
            addedReasoningEvents.removeAll(removedEvents);
        }
        initiallyPropagated = false;
        initiallyPropagatedByPatterns = false;
        removedEvents.clear();
    }

    private Map<Thread, Integer> getPorfPrefix(final Event event, final boolean readOnly) {
        final List<Map<Thread, Integer>> porfPrefixesForEvent = porfPrefixes.get(event);
        final Map<Thread, Integer> porfPrefixForEvent = porfPrefixesForEvent.get(porfPrefixesForEvent.size() - 1);
        return readOnly ? porfPrefixForEvent : new HashMap<>(porfPrefixForEvent);
    }

    private boolean checkFullConsistency(final boolean reasonsChecked) {
        if (reasoningMethod.usesReasoning()) {
            if (backtrackingPoint != null) {
                isConsistent = false;
                return false;
            }
        }
        boolean wasInitiallyPropagatedByPatterns = initiallyPropagatedByPatterns;
        if ((!reasonsChecked && reasonKnown()) || hasPattern()) {
            isConsistent = false;
            return false;
        }

        logger.debug("Checking consistency");
        ++consistencyChecks;
        consistencyCheckTimer.start();
        if (!initiallyPropagatedByPatterns) {
            solver.updateBasePredicates(trace);
            solver.updateRemainingBasePredicates(trace);
            initiallyPropagated = true;
        } else if (!initiallyPropagated || !wasInitiallyPropagatedByPatterns) {
            solver.updateRemainingBasePredicates(trace);
            initiallyPropagated = true;
        } else {
            solver.updateRemainingBasePredicates(trace, addedEvents);
        }
        final GenMCWMMSolver.Result result = solver.check(trace);
        consistencyCheckTimer.stop();
        addedEvents.clear();
        addedReasoningEvents.clear();
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

    private Map.Entry<Boolean, Boolean> assertionViolationImpossible(final boolean ignoreLast) throws SolverException, InterruptedException, InvalidConfigurationException {
        final EventData ignoredEvent = ignoreLast ? trace.removeLast() : null;
        assertionReasonEvaluationTimer.start();
        final Map.Entry<Boolean, Boolean> assertionViolationImpossible = checkIfAssertionViolationImpossible();
        assertionReasonEvaluationTimer.stop();
        if (ignoredEvent != null) {
            trace.addEvent(ignoredEvent);
            checkForExclusiveLoad(ignoredEvent);
        }
        return assertionViolationImpossible;
    }

    private Map.Entry<Boolean, Boolean> checkIfAssertionViolationImpossible() throws SolverException, InterruptedException, InvalidConfigurationException {
        final Map<Event, Set<Event>> missingReadsForCubes = new HashMap<>();
        final Set<Event> missingReadsToBeExecuted = new HashSet<>();
        boolean checkedReasons = false;
        final Map.Entry<Map<Event, List<Event>>, Map<Thread, Integer>> backwardRevisitExplorationData = exploreBackwardRevisitStores();
        final Map<Event, List<Event>> mayBackwardRevisitStores = backwardRevisitExplorationData.getKey();
        final boolean needsBackwardRevisitCheck = !mayBackwardRevisitStores.isEmpty();
        final Map<Thread, Integer> threadExplorationLimits = backwardRevisitExplorationData.getValue();
        int assertionsInTrace = 0;
        REASON_LOOP:
        for (final Map.Entry<Assert, DNF<CoreLiteral>> reasonsForAssertion : assertionReasons.entrySet()) {
            final Assert assertion = reasonsForAssertion.getKey();
            final DNF<CoreLiteral> assertionReasons = reasonsForAssertion.getValue();
            if (threadExplorationLimits.get(assertion.getThread()) >= assertion.getGlobalId()
                    && (!needsBackwardRevisitCheck || !isBackwardRevisitPossible(assertion, threadExplorationLimits, mayBackwardRevisitStores))) {
                ++assertionsInTrace;
                continue;
            }
            for (final Conjunction<CoreLiteral> reasonCube : assertionReasons.getCubes()) {
                RfLiteral falseLiteral = null;
                boolean hasTooManyFalseLiterals = false;
                final List<Event> trueLiteralReads = new ArrayList<>();
                for (final CoreLiteral lit : reasonCube.getLiterals()) {
                    final RfLiteral relLit = (RfLiteral) lit;
                    if (evaluateRfLiteral(relLit, null)) {
                        trueLiteralReads.add(relLit.getTarget());
                    } else if (falseLiteral == null) {
                        falseLiteral = relLit;
                    } else {
                        hasTooManyFalseLiterals = true;
                        break;
                    }
                }
                if (!hasTooManyFalseLiterals) {
                    if (falseLiteral == null
                            && (!needsBackwardRevisitCheck || !isBackwardRevisitPossible(reasonCube, threadExplorationLimits, mayBackwardRevisitStores))) {
                        missingReadsForCubes.clear();
                        continue REASON_LOOP;
                    } else if (falseLiteral != null) {
                        final Event falseRead = falseLiteral.getTarget();
                        if (trace.getData(falseRead).isEmpty() && (trueLiteralReads.isEmpty() || !needsBackwardRevisitCheck
                                || !isBackwardRevisitPossible(trueLiteralReads, threadExplorationLimits, mayBackwardRevisitStores))) {
                            missingReadsForCubes.computeIfAbsent(falseRead, e -> new HashSet<>()).add(falseLiteral.getSource());
                            if (!falseLiteral.encodesExecution) {
                                missingReadsToBeExecuted.add(falseRead);
                            }
                        }
                    }
                }
            }
            for (final Map.Entry<Event, Set<Event>> missingReadForCubes : missingReadsForCubes.entrySet()) {
                final Event missingRead = missingReadForCubes.getKey();
                if (!missingReadsToBeExecuted.contains(missingRead)) {
                    continue;
                }
                final Set<Event> coveredWrites = missingReadForCubes.getValue();
                final Event[] allWrites = mayRfInMap.get(missingRead);
                if (coveredWrites.size() == allWrites.length) {
                    missingReadsForCubes.clear();
                    continue REASON_LOOP;
                }
                if (!checkedReasons) {
                    if (reasonKnown()) {
                        return Map.entry(true, true);
                    } else {
                        checkedReasons = true;
                    }
                }
                boolean othersInconsistent = true;
                for (final Event mayRfWrite : allWrites) {
                    if (!coveredWrites.contains(mayRfWrite)) {
                        if (trace.getData(mayRfWrite).isPresent() || threadExplorationLimits.get(mayRfWrite.getThread()) < mayRfWrite.getGlobalId()
                                || (needsBackwardRevisitCheck && isBackwardRevisitPossible(mayRfWrite, threadExplorationLimits, mayBackwardRevisitStores))) {
                            final boolean reasonKnown = allReasons.evaluate(lit -> {
                                if (lit instanceof final RelLiteral otherRelLiteral && otherRelLiteral.getTarget() == missingRead
                                        && otherRelLiteral.getSource() == mayRfWrite && otherRelLiteral.getRelation() == rf) {
                                    return otherRelLiteral.isPositive();
                                } else {
                                    return evaluate(lit, null);
                                }
                            });
                            if (!reasonKnown) {
                                othersInconsistent = false;
                                break;
                            }
                        }
                    }
                }
                if (othersInconsistent) {
                    missingReadsForCubes.clear();
                    continue REASON_LOOP;
                }
            }
            return Map.entry(false, checkedReasons);
        }
        if (assertionsInTrace == assertionReasons.size()) {
            assertionReasoning();
        }
        return Map.entry(true, checkedReasons);
    }

    private Map.Entry<Map<Event, List<Event>>, Map<Thread, Integer>> exploreBackwardRevisitStores() {
        final Map<Event, List<Event>> mayBackwardRevisitStores = new HashMap<>();
        final Map<Thread, Integer> threadExplorationLimits = new HashMap<>();
        final Map<Thread, List<EventData>> threadEventsMap = trace.getThreadEventsMap();
        for (final Thread thread : relevantThreads) {
            final List<EventData> threadEvents = threadEventsMap.get(thread);
            if (threadEvents == null || threadEvents.isEmpty()) {
                threadExplorationLimits.put(thread, 0);
                continue;
            }
            final int maxLocalId = threadEvents.size() - 1;
            final EventData lastEventEntry = threadEvents.get(maxLocalId);
            Event event = lastEventEntry.getEvent();
            threadExplorationLimits.put(thread, event.getGlobalId());
            final Map<EventData, List<Event>> loadStoreMap = new HashMap<>();
            final Map<EventData, EventData> revisitLoadRfToCheck = new HashMap<>();
            final Map<EventData, List<EventData>> revisitLoadCoToCheck = new HashMap<>();
            final List<EventData> mayBackwardRevisitLoads = new ArrayList<>();
            final List<EventData> backwardRevisitLoads = new ArrayList<>();
            final List<EventData> safeCoLoads = new ArrayList<>();
            while ((event = event.getSuccessor()) != null) {
                if (event instanceof Store) {
                    final Event[] mayRfSet = mayRfOutMap.get(event);
                    if (mayRfSet == null) {
                        continue;
                    }
                    MAY_LOAD_LOOP:
                    for (final Event mayRfLoad : mayRfSet) {
                        final Optional<EventData> loadData;
                        if (mayRfLoad.getThread() != thread && (loadData = trace.getData(mayRfLoad)).isPresent()) {
                            final EventData loadEvent = loadData.get();
                            final int loadId = loadEvent.getId();
                            final EventData readFrom = loadEvent.getReadFrom();
                            if (readFrom.getCoSuccessor() != null) {
                                final List<EventData> coSuccessors = readFrom.getCoSuccessor().getCoSuccessors();
                                for (final EventData coSuccessor : coSuccessors) {
                                    if (coSuccessor.getId() <= loadId) {
                                        continue MAY_LOAD_LOOP;
                                    }
                                }
                                revisitLoadCoToCheck.put(loadEvent, coSuccessors);
                            } else {
                                safeCoLoads.add(loadEvent);
                            }
                            if (readFrom.getId() > loadId) {
                                revisitLoadRfToCheck.put(loadEvent, readFrom);
                            } else {
                                backwardRevisitLoads.add(loadEvent);
                            }
                            mayBackwardRevisitLoads.add(loadEvent);
                            loadStoreMap.computeIfAbsent(loadEvent, l -> new ArrayList<>()).add(event);
                        }
                    }
                }
            }
            final Event lastRead = getLastRead(threadEvents, maxLocalId);
            final Map<Thread, Integer> porfPrefix = lastRead == null ? Collections.emptyMap() : getPorfPrefix(lastRead, true);
            revisitLoadRfToCheck.entrySet().removeIf(rf -> inPorfPrefix(porfPrefix, thread, rf.getValue()));
            revisitLoadCoToCheck.entrySet().removeIf(co -> co.getValue().stream()
                    .allMatch(coStore -> inPorfPrefix(porfPrefix, thread, coStore)));
            mayBackwardRevisitLoads.removeIf(load -> inPorfPrefix(porfPrefix, thread, load)
                    || (!backwardRevisitLoads.contains(load) && revisitLoadRfToCheck.containsKey(load))
                    || (!revisitLoadCoToCheck.containsKey(load) && !safeCoLoads.contains(load)));
            for (final Map.Entry<EventData, List<Event>> loadStoreMapping : loadStoreMap.entrySet()) {
                final EventData load = loadStoreMapping.getKey();
                if (mayBackwardRevisitLoads.contains(load)) {
                    mayBackwardRevisitStores.computeIfAbsent(load.getEvent(), l -> new ArrayList<>()).addAll(loadStoreMapping.getValue());
                }
            }
        }
        return Map.entry(mayBackwardRevisitStores, threadExplorationLimits);
    }

    private boolean isBackwardRevisitPossible(final Conjunction<CoreLiteral> cube, final Map<Thread, Integer> threadExplorationLimits,
                                              final Map<Event, List<Event>> mayBackwardRevisitStores) {
        final List<Event> startEvents = new ArrayList<>();
        for (final CoreLiteral lit : cube.getLiterals()) {
            final RelLiteral rfLit = (RelLiteral) lit;
            startEvents.add(rfLit.getTarget());
        }
        return isBackwardRevisitPossible(startEvents, threadExplorationLimits, mayBackwardRevisitStores);
    }

    private boolean isBackwardRevisitPossible(final List<Event> startEvents, final Map<Thread, Integer> threadExplorationLimits,
                                              final Map<Event, List<Event>> mayBackwardRevisitStores) {
        if (startEvents.isEmpty()) return false;
        int maxId = -1;
        Event maxEvent = null;
        for (final Event e : startEvents) {
            if (maxEvent == null || e.getThread() != maxEvent.getThread() || e.getGlobalId() > maxEvent.getGlobalId()) {
                int newId = trace.getData(e).get().getId();
                if (newId > maxId) {
                    maxId = newId;
                    maxEvent = e;
                }
            }
        }
        return isBackwardRevisitPossible(maxEvent, threadExplorationLimits, mayBackwardRevisitStores);
    }

    private boolean isBackwardRevisitPossible(final Event startEvent, final Map<Thread, Integer> threadExplorationLimits, final Map<Event, List<Event>> mayBackwardRevisitStores) {
        final Optional<EventData> o = trace.getData(startEvent);
        int startTraceId = -1;
        if (o.isPresent()) {
            startTraceId = o.get().getId();
        }
        else {
            final List<EventData> threadEvents = trace.getThreadEventsMap().get(startEvent.getThread());
            final int startEventGid = startEvent.getGlobalId();
            for (int i = threadEvents.size() - 1; i >= 0; --i) {
                final EventData threadEvent = threadEvents.get(i);
                if (threadEvent.getEvent().getGlobalId() < startEventGid) {
                    startTraceId = threadEvent.getId();
                    break;
                }
            }
            if (startTraceId == -1) {
                return true;
            }
        }
        int i = 0;
        int maxId = startTraceId;
        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;
            for (final EventData eventData : trace.getElements().subList(i, maxId + 1)) {
                final EventData readFrom = eventData.getReadFrom();
                if (readFrom != null) {
                    final Thread loadThread = eventData.getThread();
                    final Event readFromEvent = readFrom.getEvent();
                    final Event event = eventData.getEvent();
                    for (final Event mayBackwardRevisitStore : mayRfInMap.get(event)) {
                        if (!(mayBackwardRevisitStore instanceof Init) && mayBackwardRevisitStore != readFromEvent) {
                            final Thread storeThread = mayBackwardRevisitStore.getThread();
                            if (storeThread == loadThread) {
                                continue;
                            }
                            if (mayBackwardRevisitStore.getGlobalId() > threadExplorationLimits.get(storeThread)) {
                                final List<Event> backwardRevisitStores = mayBackwardRevisitStores.get(event);
                                if (backwardRevisitStores != null && backwardRevisitStores.contains(mayBackwardRevisitStore)) {
                                    return true;
                                }
                            } else {
                                final Optional<EventData> traceStore = trace.getData(mayBackwardRevisitStore);
                                if (traceStore.isPresent()) {
                                    final int storeId = traceStore.get().getId();
                                    if (storeId > maxId) {
                                        maxId = storeId;
                                        hasChanged = true;
                                    }
                                }
                            }
                        }
                    }
                }
                ++i;
            }
        }
        return false;
    }

    private boolean reasonKnown() {
        if (reasoningMethod.usesReasons()) {
            reasonEvaluationTimer.start();
            EVENT_LOOP:
            for (final EventData event : addedReasoningEvents) {
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
            if (initiallyPropagatedByPatterns) {
                solver.updateBasePredicates(trace, addedEvents);
            } else {
                solver.updateBasePredicates(trace);
                initiallyPropagatedByPatterns = true;
            }
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
        for (final EventData event : addedReasoningEvents) {
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
        allReasons = allReasons.or(newReasons);
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
