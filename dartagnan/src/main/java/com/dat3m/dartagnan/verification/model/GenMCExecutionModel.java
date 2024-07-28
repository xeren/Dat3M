package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

import java.util.*;

/*
The ExecutionModel wraps a Model and extracts data from it in a more workable manner.
 */

//TODO: Add the capability to remove unnecessary init events from a model
// i.e. those that init some address which no read nor write accesses.
public class GenMCExecutionModel {

    private static final Logger logger = LogManager.getLogger(GenMCExecutionModel.class);
    
    private static final Filter baseFilter = Filter.byTag(Tag.VISIBLE);

    // ============= Model specific  =============
    private SimpleEventDomain trace;

    private final EncodingContext encodingContext;
    private final GenMCTraceEncoder traceEncoder;
    private final ProverEnvironment prover;
    private final GenMCWMMSolver solver;

    private final Map<EventData, Set<EventData>> mayAliasMap;

    // The following are a read-only views which get passed to the outside
    private Map<EventData, Set<EventData>> mayAliasMapView;

    private GenMCExecutionModel(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, ProverEnvironment prover, GenMCWMMSolver solver) {
        this.encodingContext = encodingContext;
        this.traceEncoder = traceEncoder;
        this.prover = prover;
        this.solver = solver;

        mayAliasMap = new HashMap<>();

        createViews();
    }

    public static GenMCExecutionModel withContext(EncodingContext encodingContext, GenMCTraceEncoder traceEncoder, ProverEnvironment prover) throws InvalidConfigurationException {
        return new GenMCExecutionModel(encodingContext, traceEncoder, prover,
                GenMCWMMSolver.withContext(encodingContext.getTask().getMemoryModel(), encodingContext, encodingContext.getAnalysisContext()));
    }

    public GenMCExecutionModel newInstance() throws InvalidConfigurationException {
        return GenMCExecutionModel.withContext(encodingContext, traceEncoder, prover);
    }

    private void createViews() {
        mayAliasMapView = Collections.unmodifiableMap(mayAliasMap);
    }

    //======================== Public data ===========================‚

    // General data
    public Program getProgram() {
        return getExecution().getProgram();
    }

    // Model specific data
    public ExecutionModel getExecution() {
        return solver.getExecution();
    }
    public Model getModel() {
        return getExecution().getModel();
    }
    public SimpleEventDomain getTrace() {
        return trace;
    }

    public Map<EventData, Set<EventData>> getMayAliasMap() {
        return mayAliasMapView;
    }

    // Only use for checked models
    public Optional<EventData> getData(Event e) {
        return getExecution().getData(e);
    }

    //========================== Initialization =========================

    public void initialize() throws InterruptedException, SolverException {
        List<Init> inits = getProgram().getThreadEvents(Init.class);
        List<EventData> initialEvents = new ArrayList<>(inits.size() * 3);
        for (Init init : inits) {
            for (Event initThreadEvent : init.getThread().getEvents()) {
                initialEvents.add(new EventData(initThreadEvent));
            }
        }
        trace = new SimpleEventDomain(initialEvents);
        check();
    }

    public void initialize(List<EventData> startTraceEvents, EventData revisitRead, EventData write) throws InterruptedException, SolverException {
        trace = new SimpleEventDomain(startTraceEvents);
        revisitRead.setReadFrom(write);
        check();
    }

    public void check() throws InterruptedException, SolverException {
        Model model = calculateModel();
        Filter eventFilter = Filter.intersection(baseFilter, trace.eventFilter());
        GenMCWMMSolver.Result result = solver.check(model, eventFilter);
        if (result.getStatus() == CAATSolver.Status.CONSISTENT) {
            trace = new SimpleEventDomain(getExecution().getEventList());
        } else {
            // TODO: Backtrack to consistent execution
            throw new UnsupportedOperationException("Backtracking of inconsistent executions not yet implemented");
        }
    }

    public List<EventData> copyTraceEvents() {
        List<EventData> events = trace.getElements();
        EventMap eventMap = new EventMap();
        List<EventData> copiedEvents = new ArrayList<>(events.size());
        for (EventData event : events) {
            EventData copiedEvent = eventMap.getOrCreate(event.getEvent());
            copiedEvent.setReadFrom(event.getReadFrom());
            copiedEvent.setWasExecuted(event.wasExecuted());
            copiedEvents.add(copiedEvent);
        }
        for (EventData event : copiedEvents) {
            if (event.getReadFrom() != null) {
                event.setReadFrom(eventMap.get(event.getReadFrom().getEvent()));
            }
        }
        return copiedEvents;
    }

    public Set<EventData> getPorfPrefix(EventData event) {
        Set<EventData> porfPrefix = getPorfPrefixPreviousPo(event);
        porfPrefix.addAll(getPorfPrefixPreviousRf(event));
        porfPrefix.add(event);
        return porfPrefix;
    }

    public EventData nextEvent() throws InterruptedException, SolverException {
        //TODO(TH): We might also want to extract events such as inline assertions
        // and whether they were violated or not.
        List<Thread> threadList = new ArrayList<>(getProgram().getThreads());
        Filter eventFilter = Filter.difference(baseFilter, trace.eventFilter());
        for (Thread thread : threadList) {
            Event e = thread.getEntry();
            do {
                if (!isTrue(encodingContext.execution(e))) {
                    e = e.getSuccessor();
                    continue;
                }
                if (eventFilter.apply(e)) {
                    return addEvent(e);
                }

                if (e instanceof CondJump jump && isTrue(encodingContext.jumpCondition(jump))) {
                    e = jump.getLabel();
                } else {
                    e = e.getSuccessor();
                }

            } while (e != null);
        }
        return null;
    }

    //========================== Internal methods  =========================

    private EventData addEvent(Event e) throws InterruptedException, SolverException {
        EventData data = new EventData(e);
        trace.addEvent(data);

        data.setWasExecuted(true);
        if (data.isMemoryEvent()) {
            // ===== Memory Events =====
            Set<EventData> mayAliasSet = mayAliasMap.computeIfAbsent(data, this::mayAliasSet);
            if (data.isRead()) {
                try {
                    data.setReadFrom(mayAliasSet.iterator().next());
                } catch (NoSuchElementException ex) {
                    logger.error("Read has nowhere to read from");
                    throw ex;
                }
            } else if (!data.isWrite()) {
                //FIXME: Handle other kinds of memory events such as SRCU_SYNC.
                throw new UnsupportedOperationException("Unexpected memory event " + data.getEvent());
            }

        } else if (data.isJump()) {
            // ===== Jumps =====
            // We override the meaning of execution here. A jump is executed IFF its condition was true.
            check();
            data.setWasExecuted(isTrue(encodingContext.jumpCondition((CondJump) e)));
        } else {
            //TODO: Maybe add some other events (e.g. assertions)
            // But for now all non-visible events are simply registered without
            // having any data extracted
        }
        return data;
    }

    private Set<EventData> mayAliasSet(EventData targetEvent) {
        if (!(targetEvent.getEvent() instanceof MemoryCoreEvent targetMemEvent)) {
            return Collections.emptySet();
        }
        Set<EventData> mayAliasSet = new HashSet<>();
        AliasAnalysis aliasAnalysis = encodingContext.getAnalysisContext().requires(AliasAnalysis.class);
        for (EventData event : trace.getElements()) {
            if (event.getEvent() instanceof MemoryCoreEvent memEvent && aliasAnalysis.mayAlias(targetMemEvent, memEvent)) {
                mayAliasSet.add(event);
            }
        }
        return mayAliasSet;
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

    private Model calculateModel() throws InterruptedException, SolverException {
        prover.pop();
        prover.addConstraint(traceEncoder.encodeFullTrace(trace));
        prover.push();
        prover.isUnsat();
        return prover.getModel();
    }

    // ===================================================

    private boolean isTrue(BooleanFormula formula) {
        return Boolean.TRUE.equals(getModel().evaluate(formula));
    }
}
