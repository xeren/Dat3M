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

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.integers.IntLiteral;
import com.dat3m.dartagnan.expression.processing.ExprSimplifier;
import com.dat3m.dartagnan.expression.type.IntegerType;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.core.Alloc;
import com.dat3m.dartagnan.program.event.core.Load;
import com.dat3m.dartagnan.program.event.core.Local;
import com.dat3m.dartagnan.program.event.core.Store;
import com.dat3m.dartagnan.program.event.core.threading.ThreadArgument;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.SimpleEventDomain;

import static com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator.getValue;

public class ConstantPropagationExpressionEvaluator implements ExpressionEvaluator {

    private SimpleEventDomain trace;
    private int solverCheckTime = 0;
    private int solverChecks = 0;

    private final EncodingContext encodingContext;
    private final ProverEnvironment prover;
    private final GenMCTraceEncoder traceEncoder;
    private final Map<Event, BooleanFormula> cfContext = new HashMap<>();
    private final ConstantPropagator evaluator = new ConstantPropagator();

    ConstantPropagationExpressionEvaluator(EncodingContext encodingContext, ProverEnvironment prover, GenMCTraceEncoder traceEncoder) {
        this.encodingContext = encodingContext;
        this.prover = prover;
        this.traceEncoder = traceEncoder;
    }

    @Override
    public BigInteger evaluate(Expression expr) throws SolverException, InterruptedException {
        Expression result = expr.accept(evaluator);
        if (result instanceof BoolLiteral bool) {
            return bool.getValue() ? BigInteger.ONE : BigInteger.ZERO;
        } else if (result instanceof IntLiteral num) {
            return num.getValue();
        }
        return evaluateNonDet(result);
    }

    @Override
    public boolean evaluateReachability(Expression expr) throws SolverException, InterruptedException {
        Expression result = expr.accept(evaluator);
        if (result instanceof BoolLiteral bool) {
            return bool.getValue();
        }
        return evaluateReachabilityNonDet(result);
    }

    private BigInteger evaluateNonDet(Expression expr) throws SolverException, InterruptedException {
        Model model = calculateModel();
        if (model == null) {
            return null;
        }
        return getValue(model.evaluate(encodingContext.encodeExpressionAt(expr, trace.getLast().getEvent())));
    }

    private boolean evaluateReachabilityNonDet(Expression expr) throws SolverException, InterruptedException {
        return calculateModel(expr) != null;
    }

    private Model calculateModel() throws SolverException, InterruptedException {
        return calculateModel(ExpressionFactory.getInstance().makeTrue());
    }

    private Model calculateModel(Expression assumption) throws SolverException, InterruptedException {
        Collection<BooleanFormula> assumptions = new ArrayList<>(cfContext.values());
        assumptions.add(encodingContext.encodeExpressionAsBooleanAt(assumption, trace.getLast().getEvent()));
        assumptions.add(traceEncoder.encodeFullTrace(trace));
        Model model = null;
        long start = System.currentTimeMillis();
        if (!prover.isUnsatWithAssumptions(assumptions)) {
            model = prover.getModel();
        }
        solverCheckTime += System.currentTimeMillis() - start;
        ++solverChecks;
        return model;
    }

    @Override
    public void setTrace(SimpleEventDomain trace, List<EventData> unchangedEvents) {
        this.trace = trace;
        evaluator.clear(unchangedEvents);
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
    
    private class ConstantPropagator extends ExprSimplifier {

        private final Map<Register, Map.Entry<Event, Expression>> registerValues = new HashMap<>();

        private ConstantPropagator() {
            super(true);
        }

        @Override
        public Expression visitRegister(Register register) {
            EventData lastWriter = trace.getLastRegWriter(register);
            Event lastWriterEvent = lastWriter.getEvent();
            Map.Entry<Event, Expression> registerValue = registerValues.get(register);
            if (registerValue != null && registerValue.getKey() == lastWriterEvent) {
                return registerValue.getValue();
            }
            Expression result;
            if (lastWriterEvent instanceof Local local) {
                result = local.getExpr().accept(this);
            } else if (lastWriterEvent instanceof ThreadArgument arg) {
                Expression argExpr = arg.getCreator().getArguments().get(arg.getIndex());
                result = argExpr.accept(this);
            } else if (lastWriterEvent instanceof Load) {
                Store store = (Store) lastWriter.getReadFrom().getEvent();
                result = store.getMemValue().accept(this);
            } else if (lastWriterEvent instanceof Alloc alloc) {
                result = new IntLiteral((IntegerType) alloc.getAllocationType(), BigInteger.valueOf(42));
            } else {
                throw new UnsupportedOperationException("Cannot handle RegWriter that is neither Local, ThreadArgument, Load nor Alloc (" +
                        lastWriterEvent.getClass().getSimpleName() + ")");
            }
            registerValues.put(register, Map.entry(lastWriterEvent, result));
            return result;
        }

        private void clear(List<EventData> unchangedEvents) {
            if (trace.size() == 0) {
                return;
            }
            List<Register> valuesToRemove = new ArrayList<>();
            for (Map.Entry<Register, Map.Entry<Event, Expression>> registerValue : registerValues.entrySet()) {
                Event event = registerValue.getValue().getKey();
                if (trace.getData(event).isEmpty() || SimpleEventDomain.getData(unchangedEvents, event).isPresent()) {
                    valuesToRemove.add(registerValue.getKey());
                }
            }
            EventData revisit = trace.getLast();
            Event revisitEvent = revisit.getEvent();
            if (revisitEvent instanceof Load load) {
                valuesToRemove.add(load.getResultRegister());
            }
            for (Register valueToRemove : valuesToRemove) {
                registerValues.remove(valueToRemove);
            }
        }
    }
}
