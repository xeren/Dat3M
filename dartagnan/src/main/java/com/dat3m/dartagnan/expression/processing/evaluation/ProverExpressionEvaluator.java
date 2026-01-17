package com.dat3m.dartagnan.expression.processing.evaluation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.integers.IntLiteral;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.expression.type.IntegerType;
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
import com.dat3m.dartagnan.verification.model.EventData;

public class ProverExpressionEvaluator extends ExpressionEvaluator {

    private final EncodingContext encodingContext;
    private final ProverEnvironment prover;
    private final GenMCTraceEncoder traceEncoder;

    public ProverExpressionEvaluator(final ProverEnvironment prover, final GenMCTraceEncoder traceEncoder) {
        this.encodingContext = traceEncoder.getEncodingContext();
        this.prover = prover;
        this.traceEncoder = traceEncoder;
    }

    @Override
    public ProverExpressionEvaluator newInstance() {
        final ProverExpressionEvaluator newEvaluator = new ProverExpressionEvaluator(prover, traceEncoder);
        newEvaluator.cfContext.putAll(cfContext);
        return newEvaluator;
    }

    @Override
    public BigInteger evaluate(final Expression expr, final EventData forEvent) throws SolverException, InterruptedException {
        final NonDetCollector nonDetCollector = new NonDetCollector(trace, forEvent);
        if (expr.accept(nonDetCollector)) {
            return null;
        }
        return evaluateNonDet(expr, forEvent);
    }

    @Override
    public Expression evaluateToLiteral(final Expression expr, final EventData forEvent) throws SolverException, InterruptedException {
        final BigInteger result = evaluate(expr, forEvent);
        if (result == null) {
            return null;
        }
        if (expr.getType() instanceof final BooleanType type) {
            return new BoolLiteral(type, !BigInteger.ZERO.equals(result));
        }
        if (expr.getType() instanceof final IntegerType type) {
            return new IntLiteral(type, result);
        }
        return null;
    }

    @Override
    public BigInteger evaluateNonDet(final Expression expr, final EventData forEvent) throws SolverException, InterruptedException {
        return evaluate(exprToFormula(expr, forEvent.getEvent()), new ArrayList<>());
    }

    @Override
    public BigInteger evaluateAddress(final EventData event) throws SolverException, InterruptedException {
        if (event.getEvent() instanceof final MemoryCoreEvent memEvent) {
            if (memEvent.getAddress() instanceof final MemoryObject memObj) {
                return evaluate(memObj.getAddress(), event);
            } else {
                trace.addEvent(event);
                final BigInteger ret = evaluate(memEvent.getAddress(), event);
                trace.removeLast();
                return ret;
            }
        }
        return null;
    }

    @Override
    public boolean evaluateReachability(final BooleanFormula formula) throws SolverException, InterruptedException {
        final List<BooleanFormula> assumptionWrapper = new ArrayList<>();
        assumptionWrapper.add(formula);
        return calculateModel(assumptionWrapper) != null;
    }

    public BigInteger evaluate(final Expression expr, final Collection<Expression> assumptions, final EventData forEvent) throws SolverException, InterruptedException {
        final Event e = forEvent.getEvent();
        return evaluate(exprToFormula(expr, e), exprsToBoolFormula(assumptions, e));
    }

    public BigInteger evaluate(final Formula formula, final List<BooleanFormula> assumptions) throws SolverException, InterruptedException {
        final Model model = calculateModel(assumptions);
        if (model == null) {
            return null;
        }
        return getValue(model.evaluate(formula));
    }

    @Override
    public boolean evaluateReachability(final Expression expr, final EventData forEvent) throws SolverException, InterruptedException {
        final List<BooleanFormula> exprWrapper = new ArrayList<>();
        exprWrapper.add(exprToBoolFormula(expr, forEvent.getEvent()));
        return calculateModel(exprWrapper) != null;
    }

    public Model calculateModel(final Collection<BooleanFormula> assumptions) throws SolverException, InterruptedException {
        assumptions.addAll(cfContext.values());
        assumptions.add(traceEncoder.encodeFullTrace(trace));
        final Model model;
        solverCheckTimer.start();
        if (!prover.isUnsatWithAssumptions(assumptions)) {
            model = prover.getModel();
        } else {
            model = null;
        }
        solverCheckTimer.stop();
        ++solverChecks;
        return model;
    }

    @Override
    public void addNonDetCondition(final Event event, final Expression condition, final boolean isTrue) {
        final BooleanFormula encodedCondition = exprToBoolFormula(condition, event);
        final BooleanFormula newCfContext = isTrue ? encodedCondition : encodingContext.getBooleanFormulaManager().not(encodedCondition);
        cfContext.put(event, newCfContext);
    }
    
    private List<BooleanFormula> exprsToBoolFormula(final Collection<Expression> exprs, final Event forEvent) {
        return new ArrayList<>(exprs.stream()
                .map(expr -> exprToBoolFormula(expr, forEvent))
                .toList());
    }
    
    private Formula exprToFormula(final Expression expr, final Event forEvent) {
        return encodingContext.getExpressionEncoder().encodeAt(expr, forEvent).formula();
    }

    private BooleanFormula exprToBoolFormula(final Expression expr, final Event forEvent) {
        return encodingContext.getExpressionEncoder().encodeBooleanAt(expr, forEvent).formula();
    }
}
