package com.dat3m.dartagnan.expression.processing;

import java.math.BigInteger;

import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.integers.IntBinaryExpr;
import com.dat3m.dartagnan.expression.integers.IntLiteral;
import com.dat3m.dartagnan.expression.integers.IntSizeCast;
import com.dat3m.dartagnan.expression.integers.IntUnaryExpr;
import com.dat3m.dartagnan.expression.utils.IntegerHelper;
import com.dat3m.dartagnan.program.memory.MemoryObject;

public class IntExpressionEvaluator implements ExpressionVisitor<BigInteger> {

    @Override
    public BigInteger visitIntBinaryExpression(IntBinaryExpr expr) {
        final BigInteger lhs = expr.getLeft().accept(this);
        final BigInteger rhs = expr.getRight().accept(this);
        final int bitWidth = expr.getType().getBitWidth();

        return expr.getKind().apply(lhs, rhs, bitWidth);
    }

    @Override
    public BigInteger visitIntUnaryExpression(IntUnaryExpr expr) {
        final BigInteger evaluatedExpr = expr.getOperand().accept(this);
        final int bitWidth = expr.getType().getBitWidth();
        return switch (expr.getKind()) {
            case MINUS -> IntegerHelper.neg(evaluatedExpr, bitWidth);
            case CTLZ -> IntegerHelper.ctlz(evaluatedExpr, bitWidth);
        };
    }

    @Override
    public BigInteger visitIntSizeCastExpression(IntSizeCast expr) {
        final BigInteger evaluatedExpr = expr.getOperand().accept(this);

        final int sourceWidth = expr.getSourceType().getBitWidth();
        final int targetWidth = expr.getTargetType().getBitWidth();
        if (expr.isExtension()) {
            return IntegerHelper.extend(evaluatedExpr, sourceWidth, targetWidth, expr.preservesSign());
        } else if (expr.isTruncation()) {
            return IntegerHelper.truncate(evaluatedExpr, targetWidth);
        } else {
            return evaluatedExpr;
        }
    }

    @Override
    public BigInteger visitIntLiteral(IntLiteral lit) {
        return lit.getValue();
    }

    @Override
    public BigInteger visitMemoryObject(MemoryObject memObj) {
        return BigInteger.valueOf(memObj.hashCode());
    }
}
