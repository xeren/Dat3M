package com.dat3m.dartagnan.expression.processing;

import com.dat3m.dartagnan.expression.BinaryExpression;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.UnaryExpression;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.program.misc.NonDetValue;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;

public class NonDetCollector implements ExpressionVisitor<Boolean> {

    private final GenMCEventDomain trace;
    private final Event event;

    public NonDetCollector(GenMCEventDomain trace, Event event) {
        this.trace = trace;
        this.event = event;
    }

    // =================================== General ===================================
    @Override
    public Boolean visitExpression(Expression expr) {
        return expr.getOperands()
                .stream()
                .anyMatch(op -> op.accept(this));
    }

    @Override
    public Boolean visitBinaryExpression(BinaryExpression expr) {
        return expr.getLeft().accept(this) || expr.getRight().accept(this);
    }

    @Override
    public Boolean visitUnaryExpression(UnaryExpression expr) {
        return expr.getOperand().accept(this);
    }

    // =================================== Program-specific ===================================
    @Override
    public Boolean visitRegister(Register reg) {
        EventData lastWriter = trace.getLastRegWriter(reg, event);
        if (lastWriter == null) {
            return false;
        } else {
            return lastWriter.isNonDet();
        }
    }

    @Override
    public Boolean visitFunction(Function function) {
        throw new UnsupportedOperationException("There should be no functions in the model anymore when checking for non-determinism");
    }

    @Override
    public Boolean visitMemoryObject(MemoryObject memObj) { return false; }

    @Override
    public Boolean visitLocation(Location loc) { return false; }

    @Override
    public Boolean visitNonDetValue(NonDetValue nonDet) {
        return true;
    }
    
}
