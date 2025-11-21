package com.dat3m.dartagnan.expression.processing.evaluation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.integers.IntLiteral;
import com.dat3m.dartagnan.expression.misc.GEPExpr;
import com.dat3m.dartagnan.expression.processing.ExprSimplifier;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.core.Alloc;
import com.dat3m.dartagnan.program.event.core.Load;
import com.dat3m.dartagnan.program.event.core.Local;
import com.dat3m.dartagnan.program.event.core.MemoryCoreEvent;
import com.dat3m.dartagnan.program.event.core.Store;
import com.dat3m.dartagnan.program.event.core.threading.ThreadArgument;
import com.dat3m.dartagnan.program.event.core.threading.ThreadCreate;
import com.dat3m.dartagnan.program.event.core.threading.ThreadJoin;
import com.dat3m.dartagnan.program.event.core.threading.ThreadReturn;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

import static com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator.getValue;

public class ConstantPropagationExpressionEvaluator implements ExpressionEvaluator {

    private GenMCEventDomain trace;
    private int solverCheckTime = 0;
    private int solverChecks = 0;
    private static int evaluationTime = 0;

    private final ExprEvaluationContext evalContext;
    private final Map<Event, BooleanFormula> cfContext = new HashMap<>();
    private final ConstantPropagator evaluator = new ConstantPropagator();

    ConstantPropagationExpressionEvaluator(ExprEvaluationContext evalContext) {
        this.evalContext = evalContext;
        for (Map.Entry<MemoryObject, Expression> address : evalContext.getMemoryLayout().entrySet()) {
            MemoryObject addressObject = address.getKey();
            if (addressObject.getAddress() == null) {
                addressObject.setAddress(evaluator.startPropagation(address.getValue(), null));
            }
        }
    }

    @Override
    public ConstantPropagationExpressionEvaluator newInstance() {
        ConstantPropagationExpressionEvaluator newEvaluator = new ConstantPropagationExpressionEvaluator(evalContext);
        newEvaluator.cfContext.putAll(cfContext);
        newEvaluator.evaluator.registerValues.putAll(evaluator.registerValues);
        return newEvaluator;
    }

    private GenMCTraceEncoder getTraceEncoder() throws InvalidConfigurationException {
        return evalContext.getTraceEncoder();
    }

    private EncodingContext getEncodingContext() throws InvalidConfigurationException {
        return getTraceEncoder().getEncodingContext();
    }

    private ProverEnvironment getProver() {
        return evalContext.getProver();
    }

    @Override
    public BigInteger evaluate(Expression expr, Event forEvent) throws SolverException, InterruptedException {
        Expression result = evaluator.startPropagation(expr, forEvent);
        if (result instanceof BoolLiteral bool) {
            return bool.getValue() ? BigInteger.ONE : BigInteger.ZERO;
        } else if (result instanceof IntLiteral num) {
            return num.getValue();
        }
        return null;
    }

    @Override
    public BigInteger evaluateNonDet(Expression expr, Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException {
        Model model = calculateModel();
        if (model == null) {
            return null;
        }
        return getValue(model.evaluate(getEncodingContext().getExpressionEncoder().encodeAt(expr, forEvent).formula()));
    }

    @Override
    public BigInteger evaluateAddress(EventData event) throws SolverException, InterruptedException {
        if (event.getEvent() instanceof MemoryCoreEvent memEvent) {
            return evaluate(memEvent.getAddress(), event.getEvent());
        }
        return null;
    }

    @Override
    public boolean evaluateReachability(BooleanFormula formula) throws SolverException, InterruptedException, InvalidConfigurationException {
        return calculateModel(formula) != null;
    }

    @Override
    public boolean evaluateReachability(Expression expr, Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException {
        Expression result = evaluator.startPropagation(expr, forEvent);
        if (result instanceof BoolLiteral bool) {
            return bool.getValue();
        }
        return evaluateReachabilityNonDet(result);
    }

    private boolean evaluateReachabilityNonDet(Expression expr) throws SolverException, InterruptedException, InvalidConfigurationException {
        return calculateModel(expr) != null;
    }

    private Model calculateModel() throws SolverException, InterruptedException, InvalidConfigurationException {
        return calculateModel(ExpressionFactory.getInstance().makeTrue());
    }

    private Model calculateModel(Expression assumption) throws SolverException, InterruptedException, InvalidConfigurationException {
        return calculateModel(getEncodingContext().getExpressionEncoder().encodeBooleanAt(assumption, trace.getLast().getEvent()).formula());
    }

    private Model calculateModel(BooleanFormula assumption) throws SolverException, InterruptedException, InvalidConfigurationException {
        Collection<BooleanFormula> assumptions = new ArrayList<>(cfContext.values());
        assumptions.add(assumption);
        assumptions.add(getTraceEncoder().encodeFullTrace(trace));
        Model model = null;
        long start = System.currentTimeMillis();
        if (!getProver().isUnsatWithAssumptions(assumptions)) {
            model = getProver().getModel();
        }
        solverCheckTime += System.currentTimeMillis() - start;
        ++solverChecks;
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
        evaluator.clear(changedEvent);
    }

    @Override
    public void addNonDetCondition(Event event, Expression condition, boolean isTrue) throws InvalidConfigurationException {
        BooleanFormula encodedCondition = getEncodingContext().getExpressionEncoder().encodeBooleanAt(condition, event).formula();
        BooleanFormula newCfContext = encodedCondition;
        if (!isTrue) {
            newCfContext = getEncodingContext().getBooleanFormulaManager().not(encodedCondition);
        }
        cfContext.put(event, newCfContext);
    }

    @Override
    public int getEvaluationTime() {
        return evaluationTime;
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
        private Event currentEvent = null;

        private ConstantPropagator() {
            super(true);
        }

        @Override
        public Expression visitRegister(Register register) {
            if (currentEvent == null) {
                return register;
            }
            EventData lastWriter = trace.getLastRegWriter(register, currentEvent);
            Event lastWriterEvent = lastWriter.getEvent();
            int argIndex = -1;
            if (lastWriterEvent instanceof ThreadArgument arg) {
                argIndex = arg.getIndex();
                lastWriterEvent = arg.getCreator();
            }
            Map.Entry<Event, Expression> registerValue = registerValues.get(register);
            if (registerValue != null && registerValue.getKey() == lastWriterEvent) {
                return registerValue.getValue();
            }
            Expression result;
            if (lastWriterEvent instanceof Local local) {
                result = propagate(local.getExpr(), local);
            } else if (lastWriterEvent instanceof ThreadCreate creator) {
                Expression argExpr = creator.getArguments().get(argIndex);
                result = propagate(argExpr, creator);
            } else if (lastWriterEvent instanceof Load) {
                Store store = (Store) lastWriter.getReadFrom().getEvent();
                result = propagate(store.getMemValue(), store);
            } else if (lastWriterEvent instanceof Alloc alloc) {
                result = propagate(alloc.getAllocatedObject().getAddress(), currentEvent);
            } else if (lastWriterEvent instanceof ThreadJoin join) {
                ThreadReturn threadReturn = join.getJoinThread().getEvents(ThreadReturn.class).get(0);
                result = propagate(threadReturn.getValue().get(), threadReturn);
            } else {
                throw new UnsupportedOperationException("Cannot handle RegWriter that is neither Local, ThreadArgument, Load nor Alloc (" +
                        lastWriterEvent.getClass().getSimpleName() + ")");
            }
            registerValues.put(register, Map.entry(lastWriterEvent, result));
            return result;
        }

        @Override
        public Expression visitMemoryObject(MemoryObject memObj) {
            return propagate(memObj.getAddress(), currentEvent);
        }

        @Override
        public Expression visitGEPExpression(GEPExpr gep) {
            throw new UnsupportedOperationException();
        }

        private Expression startPropagation(Expression expr, Event event) {
            long time = System.currentTimeMillis();
            Expression result = propagate(expr, event);
            evaluationTime += System.currentTimeMillis() - time;
            return result;
        }

        private Expression propagate(Expression expr, Event event) {
            Event oldEvent = currentEvent;
            currentEvent = event;
            Expression result = expr.accept(this);
            currentEvent = oldEvent;
            return result;
        }

        private void clear(Event changedEvent) {
            if (trace.size() == 0) {
                return;
            }
            List<Register> valuesToRemove = new ArrayList<>();
            for (Map.Entry<Register, Map.Entry<Event, Expression>> registerValue : registerValues.entrySet()) {
                Event event = registerValue.getValue().getKey();
                if (event == changedEvent || trace.getData(event).isEmpty()) {
                    valuesToRemove.add(registerValue.getKey());
                }
            }
            EventData revisit = trace.getLast();
            Event revisitEvent = revisit.getEvent();
            if (revisitEvent instanceof Load load) {
                valuesToRemove.add(load.getResultRegister());
            }
            valuesToRemove.forEach(registerValues::remove);
        }
    }
}
