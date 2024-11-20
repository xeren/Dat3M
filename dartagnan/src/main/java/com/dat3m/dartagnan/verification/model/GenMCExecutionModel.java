package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.processing.NonDetCollector;
import com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator;
import com.dat3m.dartagnan.expression.processing.evaluation.ProverExpressionEvaluator;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.threading.ThreadArgument;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.program.specification.AssertInline;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.utils.EventGraph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
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

import static com.dat3m.dartagnan.wmm.RelationNameRepository.RF;

/*
The ExecutionModel wraps a Model and extracts data from it in a more workable manner.
 */

//TODO: Add the capability to remove unnecessary init events from a model
// i.e. those that init some address which no read nor write accesses.
@Options
public class GenMCExecutionModel {

    private static final Logger logger = LogManager.getLogger(GenMCExecutionModel.class);

    private static int consistencyChecks = 0;
    private static int consistencyCheckTime = 0;

    // ============= Model specific  =============
    private SimpleEventDomain trace;
    private Event lastAdded = null;
    private boolean hasError = false;
    private boolean isConsistent = true;
    private boolean needsNewConsistencyCheck = true;
    private boolean lastNeedsNewConsistencyCheck = true;
    private boolean needsJumpRevisit = false;

    private final EncodingContext encodingContext;
    private final GenMCTraceEncoder traceEncoder;
    private final GenMCWMMSolver solver;
    private final BooleanFormula properties;
    private final Filter threadEventFilter;
    private final EventGraph mayRfGraph;
    private final ExpressionEvaluator exprEvaluator;
    private final ProverExpressionEvaluator modelProvider;
    
    private final List<Thread> relevantThreads;
    private final Map<EventData, Set<EventData>> mayRfMap;

    private GenMCExecutionModel(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, GenMCWMMSolver solver, BooleanFormula properties, ExpressionEvaluator exprEvaluator,
            ProverExpressionEvaluator modelProvider) {
        this.encodingContext = encodingContext;
        this.traceEncoder = traceEncoder;
        this.solver = solver;
        this.properties = properties;
        this.threadEventFilter = Filter.byEvents(getProgram().getThreadEvents());
        this.mayRfGraph = encodingContext.getAnalysisContext().requires(RelationAnalysis.class).getKnowledge(getTask().getMemoryModel().getRelation(RF)).getMaySet();
        this.exprEvaluator = exprEvaluator;
        this.modelProvider = modelProvider;

        this.relevantThreads = new ArrayList<>(getProgram().getThreads());
        this.mayRfMap = new HashMap<>();
    }

    public static GenMCExecutionModel withContext(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, ProverEnvironment prover, BooleanFormula properties,
            boolean useSMTSolver) throws InvalidConfigurationException {
        VerificationTask task = encodingContext.getTask();
        GenMCWMMSolver solver = GenMCWMMSolver.withContext(task.getMemoryModel(), encodingContext, encodingContext.getAnalysisContext());
        ExpressionEvaluator exprEvaluator = ExpressionEvaluator.getInstance(encodingContext, prover, traceEncoder, useSMTSolver);
        ProverExpressionEvaluator modelProvider = new ProverExpressionEvaluator(encodingContext, prover, traceEncoder);
        GenMCExecutionModel model = new GenMCExecutionModel(encodingContext, traceEncoder, solver, properties, exprEvaluator, modelProvider);
        return model;
    }

    public GenMCExecutionModel newInstance(int idToKeepCoBefore) throws InvalidConfigurationException {
        GenMCExecutionModel model = new GenMCExecutionModel(encodingContext, traceEncoder, solver, properties, exprEvaluator, modelProvider);
        modelProvider.revokeProhibitions(idToKeepCoBefore);
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

    // General data
    public int getSolverChecks() {
        return exprEvaluator.getSolverChecks() + modelProvider.getSolverChecks();
    }

    public int getSolverCheckTime() {
        return exprEvaluator.getSolverCheckTime() + modelProvider.getSolverCheckTime();
    }

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
    public boolean needsJumpRevisit() {
        return needsJumpRevisit;
    }

    public Map<EventData, Set<EventData>> getMayRfMap() {
        return mayRfMap;
    }

    //========================== Initialization =========================

    public void initialize() throws InterruptedException, SolverException {
        List<Init> inits = getProgram().getThreadEvents(Init.class);
        setTrace(new SimpleEventDomain(), Collections.emptyList());
        for (Init init : inits) {
            Thread initThread = init.getThread();
            relevantThreads.remove(initThread);
            for (Event initThreadEvent : initThread.getEvents()) {
                trace.addEvent(new EventData(initThreadEvent));
            }
        }
        lastAdded = null;
        isConsistent = true;
        needsNewConsistencyCheck = false;
        lastNeedsNewConsistencyCheck = false;
    }

    public void initialize(List<EventData> startTraceEvents, EventData revisitRead, EventData write, Map<Event, Event> nonDetJumpStatus, List<EventData> unchangedEvents)
            throws InterruptedException, SolverException {
        for (Map.Entry<Event, Event> nonDetJump : nonDetJumpStatus.entrySet()) {
            CondJump jump = (CondJump) nonDetJump.getKey();
            boolean wasExecuted = nonDetJump.getValue() == jump.getLabel();
            logger.debug("Status for non-deterministic jump " + jump.getGlobalId() + ": " + jump + ", executed: " + wasExecuted);
            exprEvaluator.addNonDetCondition(jump, jump.getGuard(), wasExecuted);
            modelProvider.addNonDetCondition(jump, jump.getGuard(), wasExecuted);
        }
        revisitRead.setReadFrom(write);
        initialize(new SimpleEventDomain(startTraceEvents), unchangedEvents);
    }

    public void initialize(List<EventData> startTraceEvents, EventData jump, boolean jumpIsExecuted) throws InterruptedException, SolverException {
        jump.setWasExecuted(jumpIsExecuted);
        CondJump jumpEvent = (CondJump) jump.getEvent();
        exprEvaluator.addNonDetCondition(jumpEvent, jumpEvent.getGuard(), jumpIsExecuted);
        modelProvider.addNonDetCondition(jumpEvent, jumpEvent.getGuard(), jumpIsExecuted);
        initialize(new SimpleEventDomain(startTraceEvents), Collections.emptyList());
    }

    //========================== Public methods =========================

    public boolean checkConsistency() throws InterruptedException, SolverException {
        return checkConsistency((EventData) null);
    }

    public boolean checkConsistency(EventData eventToIgnoreFrom) throws InterruptedException, SolverException {
        if ((!needsNewConsistencyCheck && eventToIgnoreFrom == null) || (!lastNeedsNewConsistencyCheck && eventToIgnoreFrom != null)) {
            return isConsistent;
        }

        logger.debug("Checking consistency");
        EventData event = null;
        List<EventData> ignoredEvents = new ArrayList<>();
        if (eventToIgnoreFrom != null) {
            while ((event = trace.removeLast()) != eventToIgnoreFrom) {
                ignoredEvents.add(0, event);
            }
            ignoredEvents.add(0, event);
        }
        long start = System.currentTimeMillis();
        ++consistencyChecks;
        Model model = modelProvider.calculateModel();
        if (model == null) {
            isConsistent = false;
            consistencyCheckTime += System.currentTimeMillis() - start;
            return false;
        }
        if (checkConsistency(model)) {
            if (eventToIgnoreFrom != null) {
                trace.addEvents(ignoredEvents);
                lastNeedsNewConsistencyCheck = false;
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
            copiedEvent.setNonDet(event.isNonDet());
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

    public Collection<EventData> getPorfPrefix(EventData event) {
        List<EventData> porfPrefix = new ArrayList<>();
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

                Optional<EventData> data = trace.getData(e);
                if (e instanceof CondJump jump && data.isPresent() && data.get().wasExecuted()) {
                    e = jump.getLabel();
                } else {
                    e = e.getSuccessor();
                }
            }
            ++threadIndex;
        }
        return null;
    }

    //========================== Internal methods  =========================

    private void initialize(SimpleEventDomain startTrace, List<EventData> unchangedEvents) {
        List<EventData> startTraceEvents = startTrace.getElements();
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
        setTrace(startTrace, unchangedEvents);
        Thread firstThread = startTrace.getElements()
                .stream()
                .map(EventData::getThread)
                .filter(relevantThreads::contains)
                .min(Comparator.comparingInt(relevantThreads::indexOf))
                .get();
        lastAdded = getLastAddedThreadEvent(firstThread);
        isConsistent = true;
        needsNewConsistencyCheck = true;
        lastNeedsNewConsistencyCheck = false;
    }

    private EventData addEvent(Event e) throws InterruptedException, SolverException {
        needsJumpRevisit = false;
        EventData data = new EventData(e);

        data.setWasExecuted(true);
        NonDetCollector nonDetCollector = new NonDetCollector(trace);
        if (data.getEvent() instanceof RegWriter writer) {
            if (writer instanceof Local local) {
                data.setNonDet(local.getExpr().accept(nonDetCollector));
            } else if (writer instanceof ThreadArgument arg) {
                Expression argExpr = arg.getCreator().getArguments().get(arg.getIndex());
                data.setNonDet(argExpr.accept(nonDetCollector));
            } else if (writer instanceof Load) {
                // handled elsewhere
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
                    data.setNonDet(true);
                    boolean wasExecuted;
                    if (!needsNewConsistencyCheck) {
                        BigInteger result = exprEvaluator.evaluate(jump.getGuard());
                        if (result == null) {
                            isConsistent = false;
                            return data;
                        }
                        wasExecuted = !BigInteger.ZERO.equals(result);
                    } else {
                        boolean isConsistent = checkConsistency();
                        Model model = modelProvider.getModel();
                        if (model == null || !isConsistent) {
                            this.isConsistent = false;
                            return data;
                        }
                        wasExecuted = model.evaluate(encodingContext.jumpCondition(jump));
                    }
                    logger.debug("Exploring non-deterministic jump " + jump);
                    data.setWasExecuted(wasExecuted);
                    exprEvaluator.addNonDetCondition(jump, jump.getGuard(), wasExecuted);
                    modelProvider.addNonDetCondition(jump, jump.getGuard(), wasExecuted);
                    needsJumpRevisit = true;
                } else {
                    BigInteger wasExecuted = exprEvaluator.evaluate(jump.getGuard());
                    if (wasExecuted != null) {
                        data.setWasExecuted(!BigInteger.ZERO.equals(wasExecuted));
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
                    List<BooleanFormula> assumptionWrapper = new ArrayList<>();
                    assumptionWrapper.add(encodingContext.encodeExpressionAsBooleanAt(assumption, assumptionEvent));
                    isConsistent = exprEvaluator.evaluateReachability(assumption);
                    if (isConsistent) {
                        exprEvaluator.addNonDetCondition(assumptionEvent, assumption, true);
                        modelProvider.addNonDetCondition(assumptionEvent, assumption, true);
                    }
                }
            }
        } else if (data.getEvent() instanceof Assert assertionEvent) {
            // ===== Assertions =====
            trace.addEvent(data);
            lastNeedsNewConsistencyCheck = needsNewConsistencyCheck;
            if (assertionEvent.getExpression() instanceof BoolLiteral bool) {
                boolean failure = !bool.getValue();
                if (failure && checkConsistency()) {
                    hasError = true;
                }
            } else if (checkConsistency()) {
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
                .map(trace::getData)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private EventData getThreadStartStore(ThreadStart thread) {
        try {
            return trace.getObjectById(trace.getId(trace.getData(thread.getCreator()).get()) + 1);
        } catch (NoSuchElementException e) {
            isConsistent = false;
            return null;
        }
    }

    private Collection<EventData> getPorfPrefixPreviousPo(EventData event) {
        Optional<EventData> previousEvent = findPoPredecessor(event);
        if (previousEvent.isPresent()) {
            return getPorfPrefix(previousEvent.get());
        }
        return new ArrayList<>();
    }

    private Optional<EventData> findPoPredecessor(EventData event) {
        Optional<EventData> predecessor = Optional.empty();
        Event currentPredecessor = event.getEvent();
        while (predecessor.isEmpty() && (currentPredecessor = currentPredecessor.getPredecessor()) != null) {
            predecessor = trace.getData(currentPredecessor);
        }
        return predecessor;
    }

    private Collection<EventData> getPorfPrefixPreviousRf(EventData event) {
        if (event.isRead()) {
            return getPorfPrefix(event.getReadFrom());
        }
        return Collections.emptyList();
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
        Model model = modelProvider.calculateModel(propertiesList);
        if (model == null) {
            return false;
        }
        ++consistencyChecks;
        long start = System.currentTimeMillis();
        boolean execWithPropertiesFound = checkConsistency(model, propertiesList);
        consistencyCheckTime += System.currentTimeMillis() - start;
        return execWithPropertiesFound;
    }

    private boolean checkConsistency(Model model) throws InterruptedException, SolverException {
        return checkConsistency(model, new ArrayList<>());
    }

    private boolean checkConsistency(Model model, List<BooleanFormula> assumptions) throws InterruptedException, SolverException {
        Filter eventFilter = Filter.intersection(Filter.byTag(Tag.VISIBLE), trace.eventFilter());
        while (model != null) {
            GenMCWMMSolver.Result result = solver.check(model, eventFilter);
            if (result.getStatus() == CAATSolver.Status.CONSISTENT) {
                modelProvider.prohibitPermanently();
                return true;
            } else {
                logger.debug("Looking for consistent coherences");
                modelProvider.prohibitCoherences(solver);
                model = modelProvider.calculateModel(assumptions);
            }
        }
        modelProvider.revokeProhibitions();
        return false;
    }

    private void setTrace(SimpleEventDomain trace, List<EventData> unchangedEvents) {
        this.trace = trace;
        exprEvaluator.setTrace(trace, unchangedEvents);
        modelProvider.setTrace(trace, unchangedEvents);
    }
}
