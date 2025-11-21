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
import com.dat3m.dartagnan.program.event.core.MemoryCoreEvent;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;

import static com.dat3m.dartagnan.encoding.GenMCTraceEncoder.getMemoryLayout;
import static com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator.getValue;

public class ProverExpressionEvaluator implements ExpressionEvaluator {

    private GenMCEventDomain trace;
    private Model model;
    private int solverCheckTime = 0;
    private int solverChecks = 0;

    private final EncodingContext encodingContext;
    private final ProverEnvironment prover;
    private final GenMCTraceEncoder traceEncoder;
    private final Map<MemoryObject, Expression> memoryLayout;
    private final Map<Event, BooleanFormula> cfContext = new HashMap<>();

    public ProverExpressionEvaluator(ProverEnvironment prover, GenMCTraceEncoder traceEncoder) {
        this.encodingContext = traceEncoder.getEncodingContext();
        this.prover = prover;
        this.traceEncoder = traceEncoder;
        this.memoryLayout = getMemoryLayout(encodingContext.getTask().getProgram().getMemory());
    }

    @Override
    public ProverExpressionEvaluator newInstance() {
        ProverExpressionEvaluator newEvaluator = new ProverExpressionEvaluator(prover, traceEncoder);
        newEvaluator.cfContext.putAll(cfContext);
        return newEvaluator;
    }

    @Override
    public BigInteger evaluate(Expression expr, Event forEvent) throws SolverException, InterruptedException {
        NonDetCollector nonDetCollector = new NonDetCollector(trace, forEvent);
        if (expr.accept(nonDetCollector)) {
            return null;
        }
        return evaluateNonDet(expr, forEvent);
    }

    @Override
    public BigInteger evaluateNonDet(Expression expr, Event forEvent) throws SolverException, InterruptedException {
        return evaluate(exprToFormula(expr, forEvent), new ArrayList<>());
    }

    @Override
    public BigInteger evaluateAddress(EventData event) throws SolverException, InterruptedException {
        if (event.getEvent() instanceof MemoryCoreEvent memEvent) {
            if (memEvent.getAddress() instanceof MemoryObject memObj) {
                return evaluate(memoryLayout.get(memObj), memEvent);
            } else {
                trace.addEvent(event);
                BigInteger ret = evaluate(memEvent.getAddress(), event.getEvent());
                trace.removeLast();
                return ret;
            }
        }
        return null;
    }

    @Override
    public boolean evaluateReachability(BooleanFormula formula) throws SolverException, InterruptedException {
        List<BooleanFormula> assumptionWrapper = new ArrayList<>();
        assumptionWrapper.add(formula);
        return calculateModel(assumptionWrapper) != null;
    }

    public BigInteger evaluate(Expression expr, Collection<Expression> assumptions, Event forEvent) throws SolverException, InterruptedException {
        return evaluate(exprToFormula(expr, forEvent), exprsToBoolFormula(assumptions, forEvent));
    }

    public BigInteger evaluate(Formula formula, List<BooleanFormula> assumptions) throws SolverException, InterruptedException {
        Model model = calculateModel(assumptions);
        if (model == null) {
            return null;
        }
        return getValue(model.evaluate(formula));
    }

    @Override
    public boolean evaluateReachability(Expression expr, Event forEvent) throws SolverException, InterruptedException {
        List<BooleanFormula> exprWrapper = new ArrayList<>();
        exprWrapper.add(exprToBoolFormula(expr, forEvent));
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
        BooleanFormula encodedCondition = exprToBoolFormula(condition, event);
        BooleanFormula newCfContext = encodedCondition;
        if (!isTrue) {
            newCfContext = encodingContext.getBooleanFormulaManager().not(encodedCondition);
        }
        cfContext.put(event, newCfContext);
    }

    @Override
    public int getEvaluationTime() {
        return solverCheckTime;
    }

    @Override
    public int getSolverCheckTime() {
        return solverCheckTime;
    }

    @Override
    public int getSolverChecks() {
        return solverChecks;
    }
    
    private List<BooleanFormula> exprsToBoolFormula(Collection<Expression> exprs, Event forEvent) {
        return new ArrayList<>(exprs.stream()
                .map(expr -> exprToBoolFormula(expr, forEvent))
                .toList());
    }
    
    private Formula exprToFormula(Expression expr, Event forEvent) {
        return encodingContext.getExpressionEncoder().encodeAt(expr, forEvent).formula();
    }

    private BooleanFormula exprToBoolFormula(Expression expr, Event forEvent) {
        return encodingContext.getExpressionEncoder().encodeBooleanAt(expr, forEvent).formula();
    }
}
