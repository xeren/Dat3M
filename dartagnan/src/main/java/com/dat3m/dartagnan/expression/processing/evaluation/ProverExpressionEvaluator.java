package com.dat3m.dartagnan.expression.processing.evaluation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;
import org.sosy_lab.java_smt.api.Formula;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.processing.NonDetCollector;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;

import static com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator.getValue;

public class ProverExpressionEvaluator implements ExpressionEvaluator {

    private GenMCEventDomain trace;
    private Model model;
    private int solverCheckTime = 0;
    private int solverChecks = 0;

    private final EncodingContext encodingContext;
    private final ProverEnvironment prover;
    private final GenMCTraceEncoder traceEncoder;
    private final Map<Event, BooleanFormula> cfContext = new HashMap<>();

    public ProverExpressionEvaluator(EncodingContext encodingContext, ProverEnvironment prover, GenMCTraceEncoder traceEncoder) {
        this.encodingContext = encodingContext;
        this.prover = prover;
        this.traceEncoder = traceEncoder;
    }

    @Override
    public ProverExpressionEvaluator newInstance() {
        ProverExpressionEvaluator newEvaluator = new ProverExpressionEvaluator(encodingContext, prover, traceEncoder);
        newEvaluator.cfContext.putAll(cfContext);
        return newEvaluator;
    }

    @Override
    public BigInteger evaluate(Expression expr) throws SolverException, InterruptedException {
        NonDetCollector nonDetCollector = new NonDetCollector(trace, null);
        if (expr.accept(nonDetCollector)) {
            return null;
        }
        return evaluateNonDet(expr);
    }

    @Override
    public BigInteger evaluateNonDet(Expression expr) throws SolverException, InterruptedException {
        return evaluate(exprToFormula(expr, trace.getLast().getEvent()), new ArrayList<>());
    }

    @Override
    public boolean evaluateReachability(BooleanFormula formula) throws SolverException, InterruptedException {
        List<BooleanFormula> assumptionWrapper = new ArrayList<>();
        assumptionWrapper.add(formula);
        return calculateModel(assumptionWrapper) != null;
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
    public void setTrace(GenMCEventDomain trace, Event changedEvent) {
        this.trace = trace;
        List<Event> cfEventsToRemove = new ArrayList<>();
        for (Event cfEvent : cfContext.keySet()) {
            if (trace.getData(cfEvent).isEmpty()) {
                cfEventsToRemove.add(cfEvent);
            }
        }
        cfEventsToRemove.forEach(cfContext::remove);
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
