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

public class ConstantPropagationExpressionEvaluator extends ExpressionEvaluator {

    private final ExprEvaluationContext evalContext;
    private final ConstantPropagator evaluator = new ConstantPropagator();

    ConstantPropagationExpressionEvaluator(final ExprEvaluationContext evalContext) {
        this.evalContext = evalContext;
    }

    @Override
    public ConstantPropagationExpressionEvaluator newInstance() {
        final ConstantPropagationExpressionEvaluator newEvaluator = new ConstantPropagationExpressionEvaluator(evalContext);
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
    public BigInteger evaluate(final Expression expr, final Event forEvent) throws SolverException, InterruptedException {
        final Expression result = evaluator.startPropagation(expr, forEvent);
        if (result instanceof final BoolLiteral bool) {
            return bool.getValue() ? BigInteger.ONE : BigInteger.ZERO;
        } else if (result instanceof IntLiteral num) {
            return num.getValue();
        }
        return null;
    }

    @Override
    public BigInteger evaluateNonDet(final Expression expr, final Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException {
        Model model = calculateModel();
        if (model == null) {
            return null;
        }
        return getValue(model.evaluate(getEncodingContext().getExpressionEncoder().encodeAt(expr, forEvent).formula()));
    }

    @Override
    public BigInteger evaluateAddress(final EventData event) throws SolverException, InterruptedException {
        if (event.getEvent() instanceof final MemoryCoreEvent memEvent) {
            return evaluate(memEvent.getAddress(), event.getEvent());
        }
        return null;
    }

    @Override
    public boolean evaluateReachability(final BooleanFormula formula) throws SolverException, InterruptedException, InvalidConfigurationException {
        return calculateModel(formula) != null;
    }

    @Override
    public boolean evaluateReachability(final Expression expr, final Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException {
        final Expression result = evaluator.startPropagation(expr, forEvent);
        if (result instanceof final BoolLiteral bool) {
            return bool.getValue();
        }
        return evaluateReachabilityNonDet(result);
    }

    private boolean evaluateReachabilityNonDet(final Expression expr) throws SolverException, InterruptedException, InvalidConfigurationException {
        return calculateModel(expr) != null;
    }

    private Model calculateModel() throws SolverException, InterruptedException, InvalidConfigurationException {
        return calculateModel(ExpressionFactory.getInstance().makeTrue());
    }

    private Model calculateModel(final Expression assumption) throws SolverException, InterruptedException, InvalidConfigurationException {
        return calculateModel(getEncodingContext().getExpressionEncoder().encodeBooleanAt(assumption, trace.getLast().getEvent()).formula());
    }

    private Model calculateModel(final BooleanFormula assumption) throws SolverException, InterruptedException, InvalidConfigurationException {
        final Collection<BooleanFormula> assumptions = new ArrayList<>(cfContext.values());
        assumptions.add(assumption);
        assumptions.add(getTraceEncoder().encodeFullTrace(trace));
        final Model model;
        solverCheckTimer.start();
        if (!getProver().isUnsatWithAssumptions(assumptions)) {
            model = getProver().getModel();
        } else {
            model = null;
        }
        solverCheckTimer.stop();
        ++solverChecks;
        return model;
    }

    @Override
    public void setTrace(final GenMCEventDomain trace, final EventData changedEvent, boolean inTraceOrder) {
        super.setTrace(trace, changedEvent, inTraceOrder);
        evaluator.clear(changedEvent, inTraceOrder);
    }

    @Override
    public void addNonDetCondition(final Event event, final Expression condition, final boolean isTrue) throws InvalidConfigurationException {
        final BooleanFormula encodedCondition = getEncodingContext().getExpressionEncoder().encodeBooleanAt(condition, event).formula();
        final BooleanFormula newCfContext = isTrue ? encodedCondition : getEncodingContext().getBooleanFormulaManager().not(encodedCondition);
        cfContext.put(event, newCfContext);
    }
    
    private class ConstantPropagator extends ExprSimplifier {

        private final Map<Register, Map.Entry<EventData, Expression>> registerValues = new HashMap<>();
        private Event currentEvent = null;

        private ConstantPropagator() {
            super(true, false);
        }

        @Override
        public Expression visitRegister(final Register register) {
            if (currentEvent == null) {
                return register;
            }
            final EventData lastWriter = trace.getLastRegWriter(register, currentEvent);
            final Event lastWriterEvent = lastWriter.getEvent();
            final Expression result;
            final Map.Entry<EventData, Expression> registerValue = registerValues.get(register);
            if (lastWriterEvent instanceof final ThreadArgument arg) {
                final ThreadCreate creator = arg.getCreator();
                final Expression argExpr = creator.getArguments().get(arg.getIndex());
                if (registerValue != null && registerValue.getKey().getEvent() == creator) {
                    return registerValue.getValue();
                }
                result = propagate(argExpr, creator);
                final EventData creatorEvent = trace.getThreadEventsMap().get(creator.getThread()).get(0);
                registerValues.put(register, Map.entry(creatorEvent, result));
                return result;
            }
            if (registerValue != null && registerValue.getKey().getEvent() == lastWriterEvent) {
                return registerValue.getValue();
            }
            if (lastWriterEvent instanceof final Local local) {
                result = propagate(local.getExpr(), local);
            } else if (lastWriterEvent instanceof Load) {
                final Store store = (Store) lastWriter.getReadFrom().getEvent();
                result = propagate(store.getMemValue(), store);
            } else if (lastWriterEvent instanceof final Alloc alloc) {
                result = propagate(alloc.getAllocatedObject().getAddress(), currentEvent);
            } else if (lastWriterEvent instanceof final ThreadJoin join) {
                final ThreadReturn threadReturn = join.getJoinThread().getEvents(ThreadReturn.class).get(0);
                final Expression returnValue = threadReturn.getValue().orElseThrow(() ->
                        new IllegalStateException("Return value of thread " + threadReturn.getThread().getName() + " is referenced but does not return anything"));
                result = propagate(returnValue, threadReturn);
            } else {
                throw new UnsupportedOperationException("Cannot handle RegWriter that is neither Local, ThreadArgument, Load nor Alloc (" +
                        lastWriterEvent.getClass().getSimpleName() + ")");
            }
            registerValues.put(register, Map.entry(lastWriter, result));
            return result;
        }

        @Override
        public Expression visitMemoryObject(final MemoryObject memObj) {
            return propagate(memObj.getAddress(), currentEvent);
        }

        @Override
        public Expression visitGEPExpression(final GEPExpr gep) {
            throw new UnsupportedOperationException();
        }

        private Expression startPropagation(final Expression expr, final Event event) {
            evaluationTimer.start();
            final Expression result = propagate(expr, event);
            evaluationTimer.stop();
            return result;
        }

        private Expression propagate(final Expression expr, final Event event) {
            final Event oldEvent = currentEvent;
            currentEvent = event;
            final Expression result = expr.accept(this);
            currentEvent = oldEvent;
            return result;
        }

        private void clear(final EventData changedEvent, final boolean inTraceOrder) {
            if (trace.size() == 0) {
                return;
            }
            final List<Register> valuesToRemove = new ArrayList<>();
            if (inTraceOrder) {
                final int changeId = changedEvent.getId();
                for (final Map.Entry<Register, Map.Entry<EventData, Expression>> registerValue : registerValues.entrySet()) {
                    if (changeId <= registerValue.getValue().getKey().getId()) {
                        valuesToRemove.add(registerValue.getKey());
                    }
                }
            } else {
                final Event change = changedEvent.getEvent();
                for (final Map.Entry<Register, Map.Entry<EventData, Expression>> registerValue : registerValues.entrySet()) {
                    final Event event = registerValue.getValue().getKey().getEvent();
                    if (event == change || trace.getData(event).isEmpty()) {
                        valuesToRemove.add(registerValue.getKey());
                    }
                }
            }
            valuesToRemove.forEach(registerValues::remove);
        }
    }
}
