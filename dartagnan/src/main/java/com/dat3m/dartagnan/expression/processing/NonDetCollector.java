package com.dat3m.dartagnan.expression.processing;

import com.dat3m.dartagnan.expression.BinaryExpression;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.UnaryExpression;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.program.misc.NonDetValue;
import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.Comparator;

public class NonDetCollector implements ExpressionVisitor<Boolean> {

    private final Domain<EventData> trace;

    public NonDetCollector(Domain<EventData> trace) {
        this.trace = trace;
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
        EventData lastWriter = trace.getElements().stream()
                .filter(event -> event.getEvent() instanceof RegWriter writer && writer.getResultRegister() == reg)
                .max(Comparator.comparingInt(EventData::getId))
                .orElse(null);
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
