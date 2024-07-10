package com.dat3m.dartagnan.expression.processing;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.dat3m.dartagnan.expression.booleans.BoolBinaryExpr;
import com.dat3m.dartagnan.expression.booleans.BoolBinaryOp;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.booleans.BoolUnaryExpr;
import com.dat3m.dartagnan.expression.booleans.BoolUnaryOp;
import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.floats.FloatCmpExpr;
import com.dat3m.dartagnan.expression.floats.FloatCmpOp;
import com.dat3m.dartagnan.expression.integers.IntCmpExpr;
import com.dat3m.dartagnan.expression.integers.IntCmpOp;

public class BoolExpressionEvaluator implements ExpressionVisitor<Boolean> {

    @Override
    public Boolean visitBoolBinaryExpression(BoolBinaryExpr expr) {
        final Boolean lhs = expr.getLeft().accept(this);
        final Boolean rhs = expr.getRight().accept(this);
        final BoolBinaryOp op = expr.getKind();

        return switch (op) {
            case AND -> lhs && rhs;
            case OR -> lhs || rhs;
            case IFF -> lhs == rhs;
        };
    }

    @Override
    public Boolean visitBoolUnaryExpression(BoolUnaryExpr expr) {
        final Boolean evaluatedExpr = expr.getOperand().accept(this);
        final BoolUnaryOp op = expr.getKind();
        return switch (op) {
            case NOT -> !evaluatedExpr;
        };
    }

    @Override
    public Boolean visitBoolLiteral(BoolLiteral lit) {
        return lit.getValue();
    }


    @Override
    public Boolean visitIntCmpExpression(IntCmpExpr expr) {
        IntExpressionEvaluator evaluator = new IntExpressionEvaluator();
        final BigInteger lhs = expr.getLeft().accept(evaluator);
        final BigInteger rhs = expr.getRight().accept(evaluator);
        final IntCmpOp op = expr.getKind();

        return op.combine(lhs, rhs);
    }

    @Override
    public Boolean visitFloatCmpExpression(FloatCmpExpr expr) {
        FloatExpressionEvaluator evaluator = new FloatExpressionEvaluator();
        final BigDecimal lhs = expr.getLeft().accept(evaluator);
        final BigDecimal rhs = expr.getRight().accept(evaluator);
        final FloatCmpOp op = expr.getKind();

        return switch (op) {
            case OEQ, UEQ -> lhs.equals(rhs);
            case ONEQ, UNEQ -> !lhs.equals(rhs);
            case OGT, UGT -> lhs.compareTo(rhs) > 0;
            case OGTE, UGTE -> lhs.compareTo(rhs) >= 0;
            case OLT, ULT -> lhs.compareTo(rhs) < 0;
            case OLTE, ULTE -> lhs.compareTo(rhs) <= 0;
            default -> visitBinaryExpression(expr);
        };
    }
}
