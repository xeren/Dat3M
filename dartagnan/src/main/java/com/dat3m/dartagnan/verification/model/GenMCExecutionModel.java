package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.configuration.GenMCReasoning;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.specification.AssertInline;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.solver.caat4wmm.PatternManager;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.AddressLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.CoreLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.ExecLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.RelLiteral;
import com.dat3m.dartagnan.utils.logic.DNF;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.utils.EventGraph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;

import static com.dat3m.dartagnan.wmm.RelationNameRepository.CO;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.RF;

/*
The ExecutionModel wraps a Model and extracts data from it in a more workable manner.
 */

//TODO: Add the capability to remove unnecessary init events from a model
// i.e. those that init some address which no read nor write accesses.
@Options
public class GenMCExecutionModel {

    private static final Logger logger = LogManager.getLogger(GenMCExecutionModel.class);
    private static final ExpressionFactory expressions = ExpressionFactory.getInstance();

    private static int consistencyChecks = 0;
    private static int consistencyCheckTime = 0;
    private static int reasonEvaluationTime = 0;
    private static int patternEvaluationTime = 0;
    private static DNF<CoreLiteral> reasons = DNF.FALSE();
    private static Map<Event, Set<Event>> mayRfInMap;
    private static Map<Event, Set<Event>> mayRfOutMap;
    private static Map<Event, Set<Event>> mayCoInMap;
    private static Map<Event, Set<Event>> mayCoOutMap;

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

    private final EncodingContext encodingContext;
    private final GenMCTraceEncoder traceEncoder;
    private final GenMCWMMSolver solver;
    private final PatternManager patterns;
    private final BooleanFormula properties;
    private final GenMCReasoning reasoningMethod;
    private final boolean underApproximateWmm;
    private final ExpressionEvaluator exprEvaluator;
    
    private final List<Thread> relevantThreads;
    private final Map<Event, List<EventData>> mayRfMap;
    private final List<EventData> addedEvents;

    private GenMCExecutionModel(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, GenMCWMMSolver solver, PatternManager patterns, BooleanFormula properties,
                                ExpressionEvaluator exprEvaluator, GenMCReasoning reasoningMethod, boolean underApproximateWmm) {
        this.encodingContext = encodingContext;
        this.traceEncoder = traceEncoder;
        this.solver = solver;
        this.patterns = patterns;
        this.properties = properties;
        this.reasoningMethod = reasoningMethod;
        this.underApproximateWmm = underApproximateWmm;
        this.exprEvaluator = exprEvaluator;

        this.relevantThreads = new ArrayList<>(getProgram().getThreads());
        this.mayRfMap = new HashMap<>();
        this.addedEvents = new ArrayList<>();
    }

    public static GenMCExecutionModel withContext(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, ProverEnvironment prover, BooleanFormula properties,
                                                  GenMCReasoning useReasoning, boolean underApproximateWmm, boolean useSMTSolver) throws InvalidConfigurationException {
        VerificationTask task = encodingContext.getTask();
        GenMCWMMSolver solver = GenMCWMMSolver.withContext(task.getMemoryModel(), encodingContext.getAnalysisContext());
        PatternManager patterns = new PatternManager(solver.getExecutionGraph());
        solver.injectExtractor(patterns);
        ExpressionEvaluator exprEvaluator = ExpressionEvaluator.getInstance(encodingContext, prover, traceEncoder, useSMTSolver);
        GenMCExecutionModel model = new GenMCExecutionModel(encodingContext, traceEncoder, solver, patterns, properties, exprEvaluator, useReasoning, underApproximateWmm);
        reasons = DNF.FALSE();
        RelationAnalysis relations = encodingContext.getAnalysisContext().requires(RelationAnalysis.class);
        EventGraph mayRfGraph = relations.getKnowledge(solver.getRf()).getMaySet();
        EventGraph mayCoGraph = relations.getKnowledge(solver.getCo()).getMaySet();
        mayRfInMap = mayRfGraph.getInMap();
        mayRfOutMap = mayRfGraph.getOutMap();
        mayCoInMap = mayCoGraph.getInMap();
        mayCoOutMap = mayCoGraph.getOutMap();
        return model;
    }

    public GenMCExecutionModel newInstance(List<EventData> startTraceEvents) throws InvalidConfigurationException {
        GenMCExecutionModel model = new GenMCExecutionModel(encodingContext, traceEncoder, solver, patterns, properties, exprEvaluator.newInstance(), reasoningMethod,
                underApproximateWmm);
        int i = 0;
        for (EventData startTraceEvent : startTraceEvents) {
            startTraceEvent.setId(i);
            ++i;
        }
        model.trace = new GenMCEventDomain(startTraceEvents, trace.getThreadEventsMap(), trace.getAddressInits(), trace.getReadWriteMap(),
                trace.getWriteReadsMap());
        if (reasoningMethod.usesPatterns()) {
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

    public static int getConsistencyCheckTime() {
        return consistencyCheckTime;
    }

    public static int getReasonEvaluationTime() {
        return reasonEvaluationTime;
    }

    public static int getPatternEvaluationTime() {
        return patternEvaluationTime;
    }

    // General data
    public int getSolverChecks() {
        return exprEvaluator.getSolverChecks();
    }

    public int getSolverCheckTime() {
        return exprEvaluator.getSolverCheckTime();
    }

    public int getEvaluationTime() {
        return exprEvaluator.getEvaluationTime();
    }

    public VerificationTask getTask() {
        return encodingContext.getTask();
    }

    public Program getProgram() {
        return encodingContext.getTask().getProgram();
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
    public void needsNewConsistencyCheck(boolean needsNewConsistencyCheck) {
        this.needsNewConsistencyCheck = needsNewConsistencyCheck;
    }
    public void lastNeedsNewConsistencyCheck(boolean lastNeedsNewConsistencyCheck) {
        this.lastNeedsNewConsistencyCheck = lastNeedsNewConsistencyCheck;
    }
    public boolean needsJumpRevisit() {
        return needsJumpRevisit;
    }

    public Collection<EventData> getMayRfSet(EventData event) {
        return mayRfMap.get(event.getEvent());
    }

    public Collection<EventData> getMayCoSet(EventData write) {
        Event writeEvent = write.getEvent();
        BigInteger writeAddress = write.getAccessedAddress();
        Set<Event> inSet = mayCoInMap.get(writeEvent);
        Set<Event> outSet = mayCoOutMap.get(writeEvent);
        List<EventData> mayCoSet = new ArrayList<>();
        EventData currentCoPredecessor = write.getCoPredecessor();
        for (EventData otherWrite : trace.getElements()) {
            if (otherWrite.getEvent() instanceof Store store && writeAddress.equals(otherWrite.getAccessedAddress()) &&
                    (otherWrite == currentCoPredecessor || ((inSet != null && inSet.contains(store)) &&
                    (otherWrite.getCoSuccessor() == null || (outSet != null && outSet.contains(otherWrite.getCoSuccessor().getEvent())))))) {
                mayCoSet.add(otherWrite);
            }
        }
        return mayCoSet;
    }

    //========================== Initialization =========================

    public void initialize() throws InterruptedException, SolverException, InvalidConfigurationException {
        List<Init> inits = getProgram().getThreadEvents(Init.class);
        setTrace(new GenMCEventDomain(), null);
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
    }

    public void initialize(EventData revisitRead, EventData write)
            throws InterruptedException, SolverException {
        setRf(revisitRead, write);
        setTrace(trace, revisitRead.getEvent());
        initFirstThreadEvent();
        initializeFields();
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
        List<EventData> mayRfSet = mayRfMap.get(read.getEvent());
        if (mayRfSet == null || mayRfSet.size() > 1) {
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
        if (mayCoSet == null || mayCoSet.size() > 1) {
            needsNewConsistencyCheck = true;
        }
    }

    public void rollback(EventData backtrackingPoint) {
        lastAdded = backtrackingPoint;
        currentThreadIndex = relevantThreads.indexOf(backtrackingPoint.getThread());
        backtrackTrace(backtrackingPoint);
        setTrace(trace, null);
        initializeFields();
    }

    public void rollbackCo(EventData coPredecessor, EventData backtrackingPoint, boolean forBackwardRevisit) {
        backtrackTrace(backtrackingPoint);
        coPredecessor.setCoSuccessor(backtrackingPoint.getCoSuccessor());
        if (coPredecessor.getCoSuccessor() != null) {
            coPredecessor.getCoSuccessor().setCoPredecessor(coPredecessor);
        }
        backtrackingPoint.setCoPredecessor(null);
        backtrackingPoint.setCoSuccessor(null);
        setTrace(trace, null);
        if (forBackwardRevisit) {
            initFirstThreadEvent();
        } else {
            lastAdded = backtrackingPoint;
            currentThreadIndex = relevantThreads.indexOf(backtrackingPoint.getThread());
        }
        initializeFields();
    }

    private void backtrackTrace(EventData backtrackingPoint) {
        List<EventData> removedEvents = trace.rollback(backtrackingPoint);
        if (reasoningMethod.usesPatterns()) {
            addedEvents.removeAll(removedEvents);
            if (!addedEvents.contains(backtrackingPoint)) {
                addedEvents.add(backtrackingPoint);
            }
        }
    }

    public void resetConsistencyStatus() {
        isConsistent = true;
    }

    private boolean checkFullConsistency(boolean ignoreLast) throws InterruptedException, SolverException {
        if ((!needsNewConsistencyCheck && !ignoreLast) || (!lastNeedsNewConsistencyCheck && ignoreLast)) {
            return isConsistent;
        }
        EventData ignoredEvent = null;
        if (ignoreLast) {
            ignoredEvent = trace.removeLast();
            if (reasoningMethod.usesPatterns()) {
                addedEvents.remove(ignoredEvent);
            }
        }
        if (reasonKnown() || hasPattern()) {
            if (ignoredEvent != null) {
                trace.addEvent(ignoredEvent);
                if (reasoningMethod.usesPatterns()) {
                    addedEvents.add(ignoredEvent);
                }
            }
            return false;
        }

        logger.debug("Checking consistency");
        long start = System.currentTimeMillis();
        ++consistencyChecks;
        GenMCWMMSolver.Result result = solver.check(trace, reasoningMethod);
        addedEvents.clear();
        initiallyPropagated = true;
        if (result.getStatus() == CAATSolver.Status.CONSISTENT) {
            if (ignoredEvent != null) {
                trace.addEvent(ignoredEvent);
                if (reasoningMethod.usesPatterns()) {
                    addedEvents.add(ignoredEvent);
                }
                lastNeedsNewConsistencyCheck = false;
            } else {
                needsNewConsistencyCheck = false;
            }
            isConsistent = true;
            consistencyCheckTime += System.currentTimeMillis() - start;
            return true;
        } else if (result.getStatus() == CAATSolver.Status.INCONSISTENT) {
            if (ignoredEvent != null) {
                trace.addEvent(ignoredEvent);
                if (reasoningMethod.usesPatterns()) {
                    addedEvents.add(ignoredEvent);
                }
            }
            isConsistent = false;
            consistencyCheckTime += System.currentTimeMillis() - start;
            reasons = reasons.or(result.getCoreReasons());
            return false;
        } else {
            throw new IllegalArgumentException("Inconclusive");
        }
    }

    public boolean checkConsistency(boolean ignoreLast) throws InterruptedException, SolverException {
        if (!underApproximateWmm) {
            return checkFullConsistency(ignoreLast);
        }
        if ((!needsNewConsistencyCheck && !ignoreLast) || (!lastNeedsNewConsistencyCheck && ignoreLast)) {
            return isConsistent;
        }
        EventData ignoredEvent = null;
        if (ignoreLast) {
            ignoredEvent = trace.removeLast();
            if (reasoningMethod.usesPatterns()) {
                addedEvents.remove(ignoredEvent);
            }
        }
        boolean consistent = !reasonKnown() && !hasPattern();
        if (ignoredEvent != null) {
            trace.addEvent(ignoredEvent);
            if (reasoningMethod.usesPatterns()) {
                addedEvents.add(ignoredEvent);
            }
        }
        return consistent;
    }

    private boolean reasonKnown() {
        long start = System.currentTimeMillis();
        boolean reasonKnown = reasoningMethod.usesReasons() && reasons.evaluate(this::evaluate);
        reasonEvaluationTime += System.currentTimeMillis() - start;
        return reasonKnown;
    }

    private boolean evaluate(CoreLiteral literal) {
        if (literal instanceof ExecLiteral lit) {
            return evaluateExecLiteral(lit);
        } else if (literal instanceof AddressLiteral loc) {
            return evaluateAddressLiteral(loc);
        } else if (literal instanceof RelLiteral lit) {
            Optional<String> relName = lit.getRelation().getName();
            if (relName.isEmpty()) {
                throw new IllegalArgumentException("Expected base relation as RelLiteral");
            }
            if (relName.get().equals(CO)) {
                return evaluateCoLiteral(lit);
            } else if (relName.get().equals(RF)) {
                return evaluateRfLiteral(lit);
            } else {
                throw new IllegalArgumentException("Expected " + CO + " or " + RF + " as relation for RelLiteral");
            }
        } else {
            throw new IllegalArgumentException("CoreLiteral " + literal + " is not supported");
        }
    }

    private boolean evaluateExecLiteral(ExecLiteral exec) {
        boolean execValue = trace.getData(exec.getEvent()).isPresent();
        return exec.isNegative() != execValue;
    }

    private boolean evaluateAddressLiteral(AddressLiteral address) {
        Optional<EventData> first = trace.getData(address.getFirst());
        Optional<EventData> second = trace.getData(address.getSecond());
        boolean addressValue;
        if (first.isEmpty() || second.isEmpty()) {
            addressValue = false;
        } else {
            addressValue = first.get().getAccessedAddress().equals(second.get().getAccessedAddress());
        }
        return address.isNegative() != addressValue;
    }

    private boolean evaluateCoLiteral(RelLiteral co) {
        Optional<EventData> suc = trace.getData(co.getTarget());
        boolean coValue = false;
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
        return co.isNegative() != coValue;
    }

    private boolean evaluateRfLiteral(RelLiteral rf) {
        Optional<EventData> read = trace.getData(rf.getTarget());
        boolean rfValue;
        if (read.isPresent()) {
            EventData readFrom = read.get().getReadFrom();
            rfValue = readFrom != null && readFrom.getEvent() == rf.getSource();
        } else {
            rfValue = false;
        }
        return rf.isNegative() != rfValue;
    }

    private boolean hasPattern() {
        if (reasoningMethod.usesPatterns()) {
            long start = System.currentTimeMillis();
            solver.updateBasePredicates(trace);
            List<EventData> checkedEvents = evaluatePatterns();
            boolean patternKnown = checkedEvents.size() != addedEvents.size();
            patternEvaluationTime += System.currentTimeMillis() - start;
            return patternKnown;
        }
        return false;
    }

    private List<EventData> evaluatePatterns() {
        Relation rf = solver.getRf();
        Relation co = solver.getCo();
        if (!initiallyPropagated) {
            for (Map.Entry<EventData, EventData> rfEntry : trace.getReadWriteMap().entrySet()) {
                if (patterns.isViolated(rfEntry.getValue(), rfEntry.getKey(), rf)) {
                    return Collections.emptyList();
                }
            }
            for (EventData addressInit : trace.getAddressInits()) {
                EventData firstOverride = addressInit.getCoSuccessor();
                if (firstOverride != null) {
                    for (EventData coSuccessor : firstOverride.getCoSuccessors()) {
                        if (patterns.isViolated(addressInit, coSuccessor, co)) {
                            return Collections.emptyList();
                        }
                    }
                }
            }
            return addedEvents;
        } else {
            List<EventData> checkedEvents = new ArrayList<>(addedEvents.size());
            for (EventData event : addedEvents) {
                EventData readFrom = event.getReadFrom();
                if (readFrom != null) {
                    if (patterns.isViolated(readFrom, event, rf)) {
                        return checkedEvents;
                    }
                } else {
                    List<Integer> alreadyCheckedIds = new ArrayList<>();
                    EventData coPredecessor = event.getCoPredecessor();
                    EventData coSuccessor = event.getCoSuccessor();
                    if (coPredecessor != null || coSuccessor != null) {
                        int eventId = event.getId();
                        alreadyCheckedIds.add(eventId);
                        if (coPredecessor != null) {
                            for (EventData anyCoPredecessor : coPredecessor.getCoPredecessors()) {
                                if (!alreadyCheckedIds.contains(anyCoPredecessor.getId())) {
                                    if (patterns.isViolated(anyCoPredecessor, event, co)) {
                                        return checkedEvents;
                                    }
                                }
                            }
                        }
                        if (coSuccessor != null) {
                            for (EventData anyCoSuccessor : coSuccessor.getCoSuccessors()) {
                                if (!alreadyCheckedIds.contains(anyCoSuccessor.getId())) {
                                    if (patterns.isViolated(event, anyCoSuccessor, co)) {
                                        return checkedEvents;
                                    }
                                }
                            }
                        }
                    }
                }
                checkedEvents.add(event);
            }
            return checkedEvents;
        }
    }

    public boolean checkProperties() throws InterruptedException, SolverException, InvalidConfigurationException {
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
            copiedEvent.setCoPredecessor(event.getCoPredecessor());
            copiedEvent.setCoSuccessor(event.getCoSuccessor());
            copiedEvent.setPoPredecessor(event.getPoPredecessor());
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
            if (event.getPoPredecessor() != null) {
                EventData newPoPredecessor = eventMap.get(event.getPoPredecessor().getEvent());
                event.setPoPredecessor(newPoPredecessor);
            }
        }
        return copiedEvents;
    }

    public List<EventData> getPorfPrefix(EventData event) {
        List<EventData> porfPrefix = getPorfPrefixPreviousPo(event);
        if (event.getReadFrom() != null) {
            porfPrefix.addAll(getPorfPrefixPreviousRf(event));
        }
        if (event.getEvent() instanceof MemoryEvent) {
            porfPrefix.add(event);
        }
        return porfPrefix;
    }

    public EventData nextEvent() throws InterruptedException, SolverException, InvalidConfigurationException {
        EventData startEvent = null;
        boolean needsInit = true;
        if (lastAdded != null) {
            startEvent = lastAdded;
            needsInit = false;
        }
        while (currentThreadIndex < relevantThreads.size()) {
            Thread thread = relevantThreads.get(currentThreadIndex);
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
                EventData event = addEvent(e);
                lastAdded = event;
                if (e instanceof MemoryEvent || e instanceof CondJump) {
                    return event;
                }
                e = e.getSuccessor();
            }
            ++currentThreadIndex;
        }
        return null;
    }

    //========================== Internal methods  =========================

    private void initializeFields() {
        isConsistent = true;
        needsNewConsistencyCheck = true;
        lastNeedsNewConsistencyCheck = false;
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
                mayRfSet.add(getThreadStartStore(start));
            } else {
                mayRfSet = mayRfSet(data);
                mayRfSet.removeIf(mayRfEvent -> !address.equals(mayRfEvent.getAccessedAddress()));
            }
            mayRfMap.put(data.getEvent(), mayRfSet);
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        } else if (data.isJump()) {
            // ===== Jumps =====
            // We override the meaning of execution here. A jump is executed IFF its condition was true.
            CondJump jump = (CondJump) e;
            trace.addEvent(data);
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
        } else if (data.getEvent() instanceof Assume assumptionEvent) {
            // ===== Assumptions =====
            if (!(trace.getLast().getEvent().getPredecessor() instanceof ThreadStart start && start.isSpawned())) {
                trace.addEvent(data);
                lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
                Expression assumption = assumptionEvent.getExpr();
                if (assumption instanceof BoolLiteral bool) {
                    isConsistent = bool.getValue();
                } else {
                    isConsistent = exprEvaluator.evaluateReachability(assumption, assumptionEvent);
                    if (isConsistent) {
                        exprEvaluator.addNonDetCondition(assumptionEvent, assumption, true);
                    }
                }
            }
        } else if (data.getEvent() instanceof Assert assertionEvent) {
            // ===== Assertions =====
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
            if (assertionEvent.getExpression() instanceof BoolLiteral bool) {
                boolean failure = !bool.getValue();
                if (failure && checkFullConsistency(false)) {
                    hasError = true;
                }
            } else if (checkFullConsistency(false)) {
                AssertInline assertion = new AssertInline(assertionEvent);
                hasError = !checkProperties(assertion.encode(encodingContext));
            }
        } else {
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
        }
        if (reasoningMethod.usesPatterns()) {
            addedEvents.add(data);
        }
        return data;
    }

    private List<EventData> mayRfSet(EventData targetEvent) {
        Map<Event, Set<Event>> mayRfMap;
        if (targetEvent.isWrite()) {
            mayRfMap = mayRfOutMap;
        } else {
            mayRfMap = mayRfInMap;
        }
        Set<Event> mayRfSet = mayRfMap.get(targetEvent.getEvent());
        if (mayRfSet == null) {
            return Collections.emptyList();
        }
        List<EventData> mayRfSetData = new ArrayList<>();
        for (Event event :  mayRfSet) {
            trace.getData(event).ifPresent(mayRfSetData::add);
        }
        return mayRfSetData;
    }

    private EventData getThreadStartStore(ThreadStart thread) {
        try {
            return trace.getObjectById(trace.getId(trace.getData(thread.getCreator()).get()) + 1);
        } catch (NoSuchElementException e) {
            isConsistent = false;
            return null;
        }
    }

    private List<EventData> getPorfPrefixPreviousPo(EventData event) {
        EventData previousEvent = event.getPoPredecessor();
        if (previousEvent != null) {
            return getPorfPrefix(previousEvent);
        }
        return new ArrayList<>();
    }

    private List<EventData> getPorfPrefixPreviousRf(EventData event) {
        return getPorfPrefix(event.getReadFrom());
    }

    private boolean checkProperties(BooleanFormula properties) throws InterruptedException, SolverException, InvalidConfigurationException {
        logger.debug("Checking properties");
        if (!exprEvaluator.evaluateReachability(properties)) {
            return false;
        }
        return checkFullConsistency(false);
    }

    private void setTrace(GenMCEventDomain trace, Event changedEvent) {
        this.trace = trace;
        exprEvaluator.setTrace(trace, changedEvent);
    }
}
