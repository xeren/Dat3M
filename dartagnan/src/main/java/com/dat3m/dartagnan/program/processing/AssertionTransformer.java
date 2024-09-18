package com.dat3m.dartagnan.program.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.base.ExpressionBase;
import com.dat3m.dartagnan.expression.booleans.BoolBinaryExpr;
import com.dat3m.dartagnan.expression.booleans.BoolBinaryOp;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.processing.AssertionCollector;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegReader;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.core.Assert;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Label;

// TODO: also for normal assertions
// TODO: Check that loads are not moved over barriers
public class AssertionTransformer implements ProgramProcessor {

    private AssertionTransformer() { }

    public static AssertionTransformer newInstance() {
        return new AssertionTransformer();
    }

    @Override
    public void run(Program program) {
        transformAssertions(program);
    }

    private void transformAssertions(Program program) {
        for (Assert assertEvent : program.getThreadEvents(Assert.class)) {
            if (assertEvent.getExpression() instanceof BoolLiteral bool && !bool.getValue()) {
                Event possibleEntry = assertEvent.getPredecessor().getPredecessor();
                if (possibleEntry instanceof Label possibleEntryLabel && possibleEntryLabel.getJumpSet().size() == 1) {
                    CondJump possibleAssertIf = possibleEntryLabel.getJumpSet().iterator().next();
                    if (possibleAssertIf.getSuccessor() instanceof CondJump possibleAssertSkip && possibleAssertSkip.isGoto() && possibleAssertSkip.getSuccessor() == possibleEntry) {
                        Expression baseAssertion = AssertionCollector.negate(possibleAssertIf.getGuard());
                        AssertionCollector assertionCollector = new AssertionCollector();
                        List<ExpressionBase<BooleanType>> assertions = baseAssertion.accept(assertionCollector);
                        BoolBinaryOp assertionOp = assertionCollector.getOperator();
                        if (assertionOp != null) {
                            Event last = possibleAssertIf.getPredecessor();
                            Event firstAfter = possibleAssertSkip.getLabel().getSuccessor();
                            if (assertionOp == BoolBinaryOp.OR) {
                                transformOrAssertions(assertEvent, assertions, last, firstAfter);
                            } else {
                                transformAndAssertions(assertEvent, assertions, last, firstAfter);
                            }
                            IdReassignment.newInstance().run(program);
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface SubAssertionTransformer {
        void transformAssertion(Assert originalAssert, List<ExpressionBase<BooleanType>> assertions, Event eventToAppendTo, Event eventToPrependBefore);
    }

    private void transformAndAssertions(Assert originalAssert, List<ExpressionBase<BooleanType>> assertions, Event eventToAppendTo, Event eventToPrependBefore) {
        BoolLiteral falseLiteral = new BoolLiteral(null, false);
        if (assertions.contains(falseLiteral)) {
            Assert newAssert = new Assert(falseLiteral, originalAssert.getErrorMessage());
            newAssert.setFunction(originalAssert.getFunction());
            newAssert.setPredecessor(eventToAppendTo);
            eventToAppendTo.setSuccessor(newAssert);
            eventToPrependBefore.setPredecessor(newAssert);
            newAssert.setSuccessor(eventToPrependBefore);
            return;
        }

        BiFunction<Event, Expression, Event> assertAppender = (lastEvent, assertion) -> {
            Assert lastAssert = (Assert) lastEvent;
            Assert newAssert = new Assert(new BoolBinaryExpr(lastAssert.getExpression(), BoolBinaryOp.AND, assertion), originalAssert.getErrorMessage());
            newAssert.setFunction(originalAssert.getFunction());
            newAssert.setPredecessor(lastAssert.getPredecessor());
            newAssert.getPredecessor().setSuccessor(newAssert);
            return newAssert;
        };
        Function<Expression, Event> assertCreator = (assertion) -> new Assert(assertion, originalAssert.getErrorMessage());

        transformAssertions(originalAssert, assertions, eventToAppendTo, eventToPrependBefore, BoolBinaryOp.OR, this::transformOrAssertions, assertAppender, assertCreator);
    }

    private void transformOrAssertions(Assert originalAssert, List<ExpressionBase<BooleanType>> assertions, Event eventToAppendTo, Event eventToPrependBefore) {
        if (assertions.contains(new BoolLiteral(null, true))) {
            eventToAppendTo.setSuccessor(eventToPrependBefore);
            eventToPrependBefore.setPredecessor(eventToAppendTo);
        }
        
        Label skipAssertLabel = new Label("label after " + eventToAppendTo);
        skipAssertLabel.setFunction(originalAssert.getFunction());
        eventToPrependBefore.setPredecessor(skipAssertLabel);
        skipAssertLabel.setSuccessor(eventToPrependBefore);
        eventToPrependBefore = skipAssertLabel;

        Assert realAssert = new Assert(new BoolLiteral(null, false), originalAssert.getErrorMessage());
        realAssert.setFunction(originalAssert.getFunction());
        eventToPrependBefore.setPredecessor(realAssert);
        realAssert.setSuccessor(eventToPrependBefore);
        eventToPrependBefore = realAssert;

        BiFunction<Event, Expression, Event> assertAppender = (lastEvent, assertion) -> {
            CondJump lastAssert = (CondJump) lastEvent;
            lastAssert.setGuard(new BoolBinaryExpr(lastAssert.getGuard(), BoolBinaryOp.OR, assertion));
            return lastAssert;
        };
        Function<Expression, Event> assertCreator = (assertion) -> new CondJump(assertion, skipAssertLabel);

        transformAssertions(originalAssert, assertions, eventToAppendTo, eventToPrependBefore, BoolBinaryOp.AND, this::transformAndAssertions, assertAppender, assertCreator);
    }

    private void transformAssertions(Assert originalAssert, List<ExpressionBase<BooleanType>> assertions, Event eventToAppendTo, Event eventToPrependBefore,
            BoolBinaryOp otherOp, SubAssertionTransformer otherTransformer, BiFunction<Event, Expression, Event> assertAppender, Function<Expression, Event> assertCreater) {
        Event lastEvent = eventToAppendTo;
        for (Expression assertion : assertions) {
            if (assertion instanceof BoolBinaryExpr boolBinaryExpr && boolBinaryExpr.getKind() == otherOp) {
                otherTransformer.transformAssertion(originalAssert, boolBinaryExpr.accept(new AssertionCollector()), lastEvent, eventToPrependBefore);
                lastEvent = eventToPrependBefore.getPredecessor();
                continue;
            }
            List<RegWriter> regWritersToMove = getRegisterWritesOnlyForAssertion(assertion, originalAssert);
            if (regWritersToMove.isEmpty()) {
                if (lastEvent instanceof CondJump lastAssert) {
                    lastEvent = assertAppender.apply(lastAssert, assertion);
                    continue;
                }
            }
            for (RegWriter regWriterToMove : regWritersToMove) {
                if (originalAssert.getPredecessors().contains(regWriterToMove)) {
                    if (regWriterToMove != lastEvent) {
                        regWriterToMove.getPredecessor().setSuccessor(regWriterToMove.getSuccessor());
                        regWriterToMove.getSuccessor().setPredecessor(regWriterToMove.getPredecessor());

                        lastEvent.setSuccessor(regWriterToMove);
                        regWriterToMove.setPredecessor(lastEvent);

                        lastEvent = regWriterToMove;
                    }
                }
            }
            Event newAssert = assertCreater.apply(assertion);
            newAssert.setFunction(originalAssert.getFunction());
            lastEvent.setSuccessor(newAssert);
            newAssert.setPredecessor(lastEvent);
            lastEvent = newAssert;
        }
        eventToPrependBefore.setPredecessor(lastEvent);
        lastEvent.setSuccessor(eventToPrependBefore);
    }

    private List<RegWriter> getRegisterWritesOnlyForAssertion(Expression assertion, Assert originalAssert) {
        List<RegWriter> regWritersOnlyForAssertion = new ArrayList<>();
        for (Register reg : assertion.getRegs()) {
            List<RegWriter> writersForReg = reg.getThread()
                    .getEvents(RegWriter.class)
                    .stream()
                    .filter(regWriter -> regWriter.getResultRegister() == reg)
                    .toList();
            for (RegWriter writerForReg : writersForReg) {
                boolean isUsedElsewhere = false;
                for (Event followingEvent : writerForReg.getSuccessors()) {
                    if (followingEvent != originalAssert) {
                        if (followingEvent instanceof RegReader followingReader && followingReader.getRegisterReads()
                                .stream()
                                .anyMatch(read -> read.register() == reg)) {
                            isUsedElsewhere = true;
                            break;
                        }
                        if (writersForReg.contains(followingEvent)) {
                            break;
                        }
                    }
                }
                if (!isUsedElsewhere) {
                    regWritersOnlyForAssertion.add(writerForReg);
                }
            }
        }
        return regWritersOnlyForAssertion;
    }
}
