package com.dat3m.dartagnan.assertions;

import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.base.ExpressionBase;
import com.dat3m.dartagnan.expression.booleans.*;
import com.dat3m.dartagnan.expression.floats.FloatCmpExpr;
import com.dat3m.dartagnan.expression.integers.IntCmpExpr;
import com.dat3m.dartagnan.expression.misc.ITEExpr;
import com.dat3m.dartagnan.expression.processing.ExprSimplifier;
import com.dat3m.dartagnan.expression.processing.ExprTransformer;
import com.dat3m.dartagnan.expression.processing.evaluation.ExpressionEvaluator;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.threading.ThreadJoin;
import com.dat3m.dartagnan.program.misc.NonDetValue;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.CoreLiteral;
import com.dat3m.dartagnan.utils.logic.Conjunction;
import com.dat3m.dartagnan.utils.logic.DNF;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.GenMCExecutionModel;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.SolverException;

import java.util.*;

public class AssertionReasoner {

    private static final ExpressionFactory expressions = ExpressionFactory.getInstance();
    private static final ExprSimplifier simplifier = new ExprSimplifier(true);
    private static final Comparator<ExecutionId> executionIdComparator = Comparator.<ExecutionId>comparingInt(id -> id.eventId).reversed();

    private final GenMCExecutionModel model;
    private final GenMCEventDomain trace;
    private final Map<Event, Event[]> mayRfInMap;
    private final ExpressionEvaluator exprEvaluator;

    private final Map<Thread, List<ExecutionId>> executionEncodedIds = new HashMap<>();
    private Thread exclusionThread;

    public AssertionReasoner(GenMCExecutionModel model, Map<Event, Event[]> mayRfInMap, ExpressionEvaluator exprEvaluator) {
        this.model = model;
        this.trace = model.getTrace();
        this.mayRfInMap = mayRfInMap;
        this.exprEvaluator = exprEvaluator;
    }

    public DNF<CoreLiteral> startAssertionReasoning(final Assert assertion) throws SolverException, InterruptedException, InvalidConfigurationException {
        executionEncodedIds.clear();
        exclusionThread = assertion.getThread();
        DNF<CoreLiteral> assertionReasons = DNF.TRUE();
        try {
            if (trace.getData(assertion).isPresent()) {
                assertionReasons = assertion.pre(expressions.makeTrue(), expressions).accept(new SplitReasoner(assertion));
            } else {
                for (Map.Entry<Event, Expression> entryEvent : getAssertionEntryPoints(assertion).entrySet()) {
                    assertionReasons = assertionReasons.and(entryEvent.getValue().accept(new SplitReasoner(entryEvent.getKey())), false);
                }
            }
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof SolverException solverException) {
                    throw solverException;
                }
                if (cause instanceof InterruptedException interruptedException) {
                    throw interruptedException;
                }
                if (cause instanceof InvalidConfigurationException invalidConfigurationException) {
                    throw invalidConfigurationException;
                }
            }
            throw e;
        }
        return assertionReasons;
    }

    private Map<Event, Expression> getAssertionEntryPoints(final Assert assertion) {
        Event event = assertion;
        Expression condition = assertion.pre(expressions.makeTrue(), expressions);
        final Map<Event, Expression> entryPoints = new HashMap<>();
        while ((event = event.getPredecessor()) != null) {
            if (event instanceof final Load read && condition.getRegs().contains(read.getResultRegister())) {
                condition = expressions.makeFalse();
            } else if (event instanceof final Label label) {
                label.resetInitPost();
                if (!(condition instanceof final BoolLiteral bool && bool.getValue()) && trace.getData(label).isEmpty()) {
                    for (final Event labelUser : event.getUsers()) {
                        if (labelUser instanceof CondJump jumpToLabel && trace.getData(labelUser).isPresent()) {
                            entryPoints.put(labelUser, expressions.makeOr(expressions.makeNot(jumpToLabel.getGuard()), condition).accept(simplifier));
                        }
                    }
                }
            } else if (event instanceof final CondJump jump && !jump.isGoto() && !(condition instanceof final BoolLiteral bool && bool.getValue())) {
                final Optional<EventData> jumpExec;
                if ((jumpExec = trace.getData(jump)).isPresent() && jumpExec.get().wasExecuted() && trace.getData(jump.getSuccessor()).isEmpty()) {
                    entryPoints.put(jump, expressions.makeOr(jump.getGuard(), condition).accept(simplifier));
                }
            } else if (event instanceof final Assume joinAssume) {
                if (joinAssume.getSuccessor() instanceof ThreadJoin && trace.getData(joinAssume).isEmpty() && trace.getData(joinAssume.getPredecessor()).isPresent()) {
                    entryPoints.put(joinAssume, expressions.makeOr(expressions.makeNot(joinAssume.getExpr()), condition).accept(simplifier));
                }
            }
            condition = event.pre(condition, expressions).accept(simplifier);
        }
        return entryPoints;
    }

    private DNF<CoreLiteral> reasoning(final Event event, final Expression condition) {
        if (event == null) {
            return DNF.TRUE();
        }
        if (event instanceof final Load read) {
            final Set<Register> conditionRegs = condition.getRegs();
            final Register readReg = read.getResultRegister();
            if (conditionRegs.contains(readReg)) {
                boolean allStoresFulfill = true;
                for (final Event mayWrite : mayRfInMap.get(event)) {
                    final Store mayStore = (Store) mayWrite;
                    if (!(readReg.replaceIn(condition, mayStore.getMemValue()).accept(simplifier) instanceof final BoolLiteral bool && bool.getValue())) {
                        allStoresFulfill = false;
                        break;
                    }
                }
                if (allStoresFulfill) {
                    return reasoning(event.getPredecessor(), expressions.makeTrue());
                }
                final Optional<EventData> traceRead = trace.getData(event);
                if (traceRead.isPresent()) {
                    final EventData loadEvent = traceRead.get();
                    final EventData storeEvent = loadEvent.getReadFrom();
                    final Store write = (Store) storeEvent.getEvent();
                    if (condition instanceof final IntCmpExpr cmpExpr) {
                        Register cmpReg = null;
                        if (cmpExpr.getLeft() == readReg && cmpExpr.getRight() instanceof final Register otherReg) {
                            cmpReg = otherReg;
                        } else if (cmpExpr.getRight() == readReg && cmpExpr.getLeft() instanceof final Register otherReg) {
                            cmpReg = otherReg;
                        }
                        if (cmpReg != null) {
                            final EventData lastRegWriter = trace.getLastRegWriter(cmpReg, loadEvent);
                            if (lastRegWriter.getEvent() instanceof final Load cmpLoad && (lastRegWriter.getReadFrom() == storeEvent)) {
                                final DNF<CoreLiteral> reason = new DNF<>(newRfLiteralIfNeeded(lastRegWriter.getReadFrom().getEvent(), cmpLoad)
                                        .and(newRfLiteralIfNeeded(write, event)));
                                if (reason.isTriviallyTrue()) {
                                    return reasoning(event.getPredecessor(), expressions.makeTrue());
                                } else {
                                    return reason;
                                }
                            }
                        }
                    }
                    Expression newCondition = condition;
                    for (final Register reg : conditionRegs) {
                        if (reg != readReg) {
                            newCondition = reg.replaceIn(newCondition, evaluateSafely(reg, loadEvent));
                        }
                    }
                    boolean hasRfChoice = mayRfInMap.get(event).length > 1;
                    if (hasRfChoice) {
                        addExecutionId(event);
                        addExecutionId(write);
                    }
                    final DNF<CoreLiteral> rfReasoning = reasoning(write, readReg.replaceIn(newCondition, write.getMemValue()));
                    final DNF<CoreLiteral> poReasoning = reasoning(event.getPredecessor(),
                            readReg.replaceIn(condition, evaluateSafely(write.getMemValue(), storeEvent)).accept(simplifier));
                    final DNF<CoreLiteral> rfReasons = rfReasoning.and(poReasoning, false);
                    if (hasRfChoice) {
                        final boolean storeEncodesExecution = removeExecutionId(write);
                        final boolean loadEncodesExecution = removeExecutionId(event);
                        return rfReasons.and(new DNF<>(newRfLiteral(write, event, storeEncodesExecution || loadEncodesExecution)));
                    } else {
                        return rfReasons;
                    }
                } else {
                    return reasoning(event.getPredecessor(), expressions.makeFalse());
                }
            } else {
                return reasoning(event.getPredecessor(), condition);
            }
        }
        if (event instanceof final Label label) {
            final DNF<CoreLiteral> result = reasoning(event.getPredecessor(), event.pre(condition, expressions));
            label.resetPost();
            return result;
        }
        if (event instanceof final CondJump jump) {
            final Label label = jump.getLabel();
            final boolean labelExecuted = label.getPost() != null;
            final Expression labelPost = labelExecuted ? label.getPost() : expressions.makeTrue();
            if (jump.isGoto() || labelPost.equals(condition)) {
                return labelPost.accept(new SplitReasoner(event));
            }
            if (labelPost instanceof final BoolLiteral labelBool && labelBool.getValue()) {
                if (!(condition instanceof BoolLiteral conditionBool && !conditionBool.getValue())) {
                    return reasoning(event.getPredecessor(), condition);
                }
            }
            if (condition instanceof final BoolLiteral bool && bool.getValue()) {
                if (!(labelPost instanceof BoolLiteral)) {  // labelPost=true already covered by previous if
                    return labelPost.accept(new SplitReasoner(event));
                }
            }
            final ExecutionId currentExecutionId = getCurrentExecutionId(event);
            if (currentExecutionId != null && currentExecutionId.eventId < label.getGlobalId()) {
                currentExecutionId.executionNeeded();
                return reasoning(event.getPredecessor(), condition);
            }
            final Optional<EventData> jumpEvent = trace.getData(jump);
            if (jumpEvent.isPresent()) {
                if (jumpEvent.get().wasExecuted()) {
                    return expressions.makeAnd(jump.getGuard(), labelPost).accept(simplifier).accept(new SplitReasoner(event));
                } else {
                    return expressions.makeAnd(expressions.makeNot(jump.getGuard()), condition).accept(simplifier).accept(new SplitReasoner(event));
                }
            } else {
                return event.pre(condition, expressions).accept(simplifier).accept(new SplitReasoner(event));
            }
        }
        if (event instanceof Alloc alloc) {
            return alloc.getResultRegister().replaceIn(condition, alloc.getAllocatedObject().getAddress()).accept(simplifier).accept(new SplitReasoner(event));
        }
        if (event instanceof RegWriter || event instanceof Assume) {
            return event.pre(condition, expressions).accept(simplifier).accept(new SplitReasoner(event));
        }
        return reasoning(event.getPredecessor(), condition);
    }

    private Expression evaluateSafely(final Expression expr, final EventData event) {
        try {
            return exprEvaluator.evaluateToLiteral(expr, event);
        } catch (SolverException | InterruptedException | InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private GenMCExecutionModel.RfLiteral newRfLiteral(final Event e1, final Event e2, final boolean encodesExecution) {
        return model.new RfLiteral(e1, e2, encodesExecution);
    }

    private Conjunction<CoreLiteral> newRfLiteralIfNeeded(final Event e1, final Event e2) {
        return mayRfInMap.get(e2).length == 1 ? Conjunction.TRUE() : new Conjunction<>(model.new RfLiteral(e1, e2, true));
    }

    private static class ExecutionId {
        private final int eventId;
        private boolean isExecutionNeeded = false;

        private ExecutionId(Event event) {
            eventId = event.getGlobalId();
        }

        private void executionNeeded() {
            isExecutionNeeded = true;
        }
    }

    private void addExecutionId(final Event event) {
        List<ExecutionId> eventExecutionIds = executionEncodedIds.computeIfAbsent(event.getThread(), t -> new ArrayList<>());
        eventExecutionIds.add(new ExecutionId(event));
        eventExecutionIds.sort(executionIdComparator);
    }

    private boolean eventExecuted(Event event) {
        ExecutionId currentExecutionId = getCurrentExecutionId(event);
        if (currentExecutionId != null) {
            currentExecutionId.executionNeeded();
            return true;
        }
        return false;
    }

    private ExecutionId getCurrentExecutionId(Event event) {
        final Thread eventThread = event.getThread();
        final List<ExecutionId> executionIds = executionEncodedIds.get(eventThread);
        if (executionIds != null && !executionIds.isEmpty()) {
            return executionIds.get(executionIds.size() - 1);
        }
        return null;
    }

    private boolean removeExecutionId(final Event event) {
        final List<ExecutionId> eventExecutionIds = executionEncodedIds.get(event.getThread());
        final int eventGid = event.getGlobalId();
        boolean encodesExecution = false;
        ExecutionId eventExecutionId = null;
        for (final ExecutionId entry : eventExecutionIds) {
            if (entry.eventId == eventGid) {
                encodesExecution = entry.isExecutionNeeded;
                eventExecutionId = entry;
                break;
            }
        }
        eventExecutionIds.remove(eventExecutionId);
        return encodesExecution;
    }

    private class SplitReasoner implements ExpressionVisitor<DNF<CoreLiteral>> {

        private final Event event;
        private final Event previousEvent;

        private SplitReasoner(final Event event) {
            this.event = event;
            this.previousEvent = event.getPredecessor();
        }

        @Override
        public DNF<CoreLiteral> visitBoolBinaryExpression(BoolBinaryExpr expr) {
            expr = ExprTransformer.transformIffExpression(expr, BoolBinaryOp.AND);
            final Expression first = expr.getLeft();
            final Expression second = expr.getRight();
            if (expr.getKind() == BoolBinaryOp.AND) {
                return visitSimpleBoolExpression(expr);
            } else {
                final Optional<EventData> eventData = trace.getData(event);
                if (eventData.isPresent()) {
                    final EventData e = eventData.get();
                    if (literalToBool(evaluateSafely(first, e))) {
                        final DNF<CoreLiteral> firstReasons = first.accept(this);
                        if (literalToBool(evaluateSafely(second, e))) {
                            return firstReasons.or(second.accept(this));
                        } else {
                            return firstReasons;
                        }
                    } else {
                        return second.accept(this);
                    }
                } else {
                    return first.accept(this).or(second.accept(this));
                }
            }
        }
        @Override
        public DNF<CoreLiteral> visitBoolUnaryExpression(final BoolUnaryExpr expr) {
            if (expr.getKind() == BoolUnaryOp.NOT) {
                final Expression operand = expr.getOperand();
                final Expression negation = ExprTransformer.negate(operand);
                if (negation == null) {
                    if (operand instanceof NonDetValue) {
                        return DNF.FALSE();
                    } else {
                        assert operand instanceof Register;
                        return visitSimpleBoolExpression(expr);
                    }
                }
                return negation.accept(this);
            }
            return ExpressionVisitor.super.visitBoolUnaryExpression(expr);
        }
        @Override
        public DNF<CoreLiteral> visitBoolLiteral(BoolLiteral lit) {
            if (lit.getValue() && trace.getData(event).isPresent() && event.getThread() != exclusionThread && eventExecuted(event)) {
                return DNF.TRUE();
            }
            return visitSimpleBoolExpression(lit);
        }
        @Override
        public DNF<CoreLiteral> visitITEExpression(final ITEExpr expr) {
            return ExprTransformer.transformITEExpression(expr, BoolBinaryOp.OR).accept(this);
        }
        @Override
        public DNF<CoreLiteral> visitIntCmpExpression(final IntCmpExpr expr) {
            return visitSimpleBoolExpression(expr);
        }
        @Override
        public DNF<CoreLiteral> visitFloatCmpExpression(final FloatCmpExpr expr) {
            return visitSimpleBoolExpression(expr);
        }
        @Override
        public DNF<CoreLiteral> visitRegister(final Register reg) {
            return isBoolean(reg) ? visitSimpleBoolExpression(reg) : ExpressionVisitor.super.visitRegister(reg);
        }
        @Override
        public DNF<CoreLiteral> visitNonDetValue(final NonDetValue nonDet) {
            return isBoolean(nonDet) ? visitSimpleBoolExpression(expressions.makeFalse()) : ExpressionVisitor.super.visitNonDetValue(nonDet);
        }

        private boolean literalToBool(final Expression value) {
            return value == null || (value instanceof BoolLiteral bool && bool.getValue());
        }

        private boolean isBoolean(final Expression expr) {
            if (expr instanceof final ExpressionBase<?> typedExpr) {
                return typedExpr.getType() instanceof BooleanType;
            }
            return false;
        }

        private DNF<CoreLiteral> visitSimpleBoolExpression(final Expression boolExpr) {
            return reasoning(previousEvent, boolExpr);
        }
    }
}
