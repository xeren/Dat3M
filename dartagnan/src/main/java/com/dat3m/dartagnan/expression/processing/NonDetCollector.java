package com.dat3m.dartagnan.expression.processing;

import com.dat3m.dartagnan.expression.BinaryExpression;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.UnaryExpression;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.core.Alloc;
import com.dat3m.dartagnan.program.event.core.Load;
import com.dat3m.dartagnan.program.event.core.Local;
import com.dat3m.dartagnan.program.event.core.Store;
import com.dat3m.dartagnan.program.event.core.threading.ThreadArgument;
import com.dat3m.dartagnan.program.event.core.threading.ThreadJoin;
import com.dat3m.dartagnan.program.event.core.threading.ThreadReturn;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.program.misc.NonDetValue;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;

public class NonDetCollector implements ExpressionVisitor<Boolean> {

    private final GenMCEventDomain trace;
    private final EventData event;

    public NonDetCollector(final GenMCEventDomain trace, final EventData event) {
        this.trace = trace;
        this.event = event;
    }

    // =================================== General ===================================
    @Override
    public Boolean visitExpression(final Expression expr) {
        return expr.getOperands()
                .stream()
                .anyMatch(op -> op.accept(this));
    }

    @Override
    public Boolean visitBinaryExpression(final BinaryExpression expr) {
        return expr.getLeft().accept(this) || expr.getRight().accept(this);
    }

    @Override
    public Boolean visitUnaryExpression(final UnaryExpression expr) {
        return expr.getOperand().accept(this);
    }

    // =================================== Program-specific ===================================
    @Override
    public Boolean visitRegister(final Register reg) {
        final EventData lastWriter = trace.getLastRegWriter(reg, event);
        if (lastWriter == null) {
            return false;
        } else {
            final NonDetCollector registerCollector = new NonDetCollector(trace, lastWriter);
            if (lastWriter.getEvent() instanceof final RegWriter writer) {
                if (writer instanceof final Local local) {
                    lastWriter.setNonDet(local.getExpr().accept(registerCollector));
                } else if (writer instanceof final ThreadArgument arg) {
                    Expression argExpr = arg.getCreator().getArguments().get(arg.getIndex());
                    lastWriter.setNonDet(argExpr.accept(registerCollector));
                } else if (writer instanceof Load) {
                    Store store = (Store) lastWriter.getReadFrom().getEvent();
                    lastWriter.setNonDet(store.getMemValue().accept(registerCollector));
                } else if (writer instanceof Alloc) {
                    lastWriter.setNonDet(false);
                } else if (writer instanceof final ThreadJoin join) {
                    final ThreadReturn threadReturn = join.getJoinThread().getEvents(ThreadReturn.class).get(0);
                    final Expression returnValue = threadReturn.getValue().orElseThrow(() ->
                            new IllegalStateException("Return value of thread " + threadReturn.getThread().getName() + " is referenced but does not return anything"));
                    return returnValue.accept(registerCollector);
                }
            }
            return lastWriter.isNonDet();
        }
    }

    @Override
    public Boolean visitFunction(final Function function) {
        throw new UnsupportedOperationException("There should be no functions in the model anymore when checking for non-determinism");
    }

    @Override
    public Boolean visitMemoryObject(final MemoryObject memObj) { return false; }

    @Override
    public Boolean visitNonDetValue(final NonDetValue nonDet) {
        return true;
    }
    
}
