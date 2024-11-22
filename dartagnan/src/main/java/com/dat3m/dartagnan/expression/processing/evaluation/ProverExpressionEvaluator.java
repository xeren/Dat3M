package com.dat3m.dartagnan.expression.processing.evaluation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;
import org.sosy_lab.java_smt.api.Formula;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCWMMSolver;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.SimpleEventDomain;

import static com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator.getValue;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.CO;

public class ProverExpressionEvaluator implements ExpressionEvaluator {

    private SimpleEventDomain trace;
    private Model model;
    private int solverCheckTime = 0;
    private int solverChecks = 0;

    private final EncodingContext encodingContext;
    private final ProverEnvironment prover;
    private final GenMCTraceEncoder traceEncoder;
    private final Map<Event, BooleanFormula> cfContext = new HashMap<>();
    private final Map<BooleanFormula, Integer> prohibitedCoherences = new HashMap<>();

    private List<BooleanFormula> locallyProhibitedCoherences = new ArrayList<>();

    public ProverExpressionEvaluator(EncodingContext encodingContext, ProverEnvironment prover, GenMCTraceEncoder traceEncoder) {
        this.encodingContext = encodingContext;
        this.prover = prover;
        this.traceEncoder = traceEncoder;
    }

    @Override
    public BigInteger evaluate(Expression expr) throws SolverException, InterruptedException {
        return evaluate(exprToFormula(expr, trace.getLast().getEvent()), new ArrayList<>());
    }

    public BigInteger evaluate(Expression expr, Collection<Expression> assumptions) throws SolverException, InterruptedException {
        return evaluate(exprToFormula(expr, trace.getLast().getEvent()), exprsToBoolFormula(assumptions));
    }

    public BigInteger evaluate(Formula formula, List<BooleanFormula> assumptions) throws SolverException, InterruptedException {
        Model model = calculateModel(assumptions);
        if (model == null) {
            return null;
        }
        return getValue(model.evaluate(formula));
    }

    @Override
    public boolean evaluateReachability(Expression expr) throws SolverException, InterruptedException {
        List<BooleanFormula> exprWrapper = new ArrayList<>();
        exprWrapper.add(encodingContext.encodeExpressionAsBooleanAt(expr, trace.getLast().getEvent()));
        return calculateModel(exprWrapper) != null;
    }

    public Model calculateModel() throws SolverException, InterruptedException {
        return calculateModel(new ArrayList<>());
    }

    public Model calculateModel(Collection<BooleanFormula> assumptions) throws SolverException, InterruptedException {
        assumptions.addAll(prohibitedCoherences.keySet());
        assumptions.addAll(cfContext.values());
        assumptions.add(traceEncoder.encodeFullTrace(trace));
        Model model = null;
        long start = System.currentTimeMillis();
        if (!prover.isUnsatWithAssumptions(assumptions)) {
            model = prover.getModel();
        }
        solverCheckTime += System.currentTimeMillis() - start;
        ++solverChecks;
        this.model = model;
        return model;
    }

    public Model getModel() {
        return model;
    }

    @Override
    public void setTrace(SimpleEventDomain trace, List<EventData> unchangedEvents) {
        this.trace = trace;
        List<Event> cfEventsToRemove = new ArrayList<>();
        for (Event cfEvent : cfContext.keySet()) {
            if (trace.getData(cfEvent).isEmpty()) {
                cfEventsToRemove.add(cfEvent);
            }
        }
        for (Event cfEventToRemove : cfEventsToRemove) {
            cfContext.remove(cfEventToRemove);
        }
    }

    @Override
    public void addNonDetCondition(Event event, Expression condition, boolean isTrue) {
        BooleanFormula encodedCondition = encodingContext.encodeExpressionAsBooleanAt(condition, event);
        BooleanFormula newCfContext = encodedCondition;
        if (!isTrue) {
            newCfContext = encodingContext.getBooleanFormulaManager().not(encodedCondition);
        }
        cfContext.put(event, newCfContext);
    }

    public void prohibitCoherences(GenMCWMMSolver solver) {
        BooleanFormulaManager bmgr = encodingContext.getBooleanFormulaManager();
        EncodingContext.EdgeEncoder co = encodingContext.edge(encodingContext.getTask().getMemoryModel().getRelation(CO));
        Map<BigInteger, List<EventData>> coherences = solver.getExecution().getCoherenceMap();
        List<BooleanFormula> enc = new ArrayList<>();
        int maxId = -1;
        for (List<EventData> addressCoherence : coherences.values()) {
            Event pred = null;
            for (EventData eventData : addressCoherence) {
                Event event = eventData.getEvent();
                int eventId = trace.getId(trace.getData(event));
                if (eventId > maxId) {
                    maxId = eventId;
                }
                if (pred != null) {
                    BooleanFormula encodedCoEdge = co.encode(pred, event);
                    if (bmgr.isFalse(encodedCoEdge)) {
                        throw new IllegalArgumentException("The SMT solver returned a coherence edge that cannot be true in trace " + trace + "\nco(" + pred.getGlobalId() + ", "
                                + event.getGlobalId() + ")" + traceEncoder.encodeFullTrace(trace));
                    }
                    enc.add(encodedCoEdge);
                }
                pred = event;
            }
        }
        BooleanFormula prohibitionFormula = bmgr.not(bmgr.and(enc));
        prohibitedCoherences.put(prohibitionFormula, maxId);
        locallyProhibitedCoherences.add(prohibitionFormula);
    }

    public void prohibitPermanently() {
        locallyProhibitedCoherences = new ArrayList<>();
    }

    public void revokeProhibitions() {
        locallyProhibitedCoherences.forEach(prohibitedCoherences::remove);
        locallyProhibitedCoherences = new ArrayList<>();
    }

    public void revokeProhibitions(int idToKeepCoBefore) {
        List<BooleanFormula> cosToRevoke = new ArrayList<>();
        for (Map.Entry<BooleanFormula, Integer> prohibitedCoherence : prohibitedCoherences.entrySet()) {
            int currentMaxCoId = prohibitedCoherence.getValue();
            if (currentMaxCoId >= idToKeepCoBefore) {
                cosToRevoke.add(prohibitedCoherence.getKey());
            }
        }
        for (BooleanFormula coToRevoke : cosToRevoke) {
            prohibitedCoherences.remove(coToRevoke);
        }
    }

    @Override
    public int getSolverCheckTime() {
        return solverCheckTime;
    }

    @Override
    public int getSolverChecks() {
        return solverChecks;
    }
    
    private List<BooleanFormula> exprsToBoolFormula(Collection<Expression> exprs) {
        Event atEvent = trace.getLast().getEvent();
        return new ArrayList<>(exprs.stream()
                .map(expr -> encodingContext.encodeExpressionAsBooleanAt(expr, atEvent))
                .toList());
    }
    
    private Formula exprToFormula(Expression expr, Event atEvent) {
        return encodingContext.encodeExpressionAt(expr, atEvent);
    }
}
