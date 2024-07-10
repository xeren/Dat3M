package com.dat3m.dartagnan.expression.processing;

import java.math.BigDecimal;

import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.floats.FloatBinaryExpr;
import com.dat3m.dartagnan.expression.floats.FloatBinaryOp;
import com.dat3m.dartagnan.expression.floats.FloatLiteral;
import com.dat3m.dartagnan.expression.floats.FloatUnaryExpr;
import com.dat3m.dartagnan.expression.floats.FloatUnaryOp;

public class FloatExpressionEvaluator implements ExpressionVisitor<BigDecimal> {

    @Override
    public BigDecimal visitFloatBinaryExpression(FloatBinaryExpr expr) {
        final BigDecimal lhs = expr.getLeft().accept(this);
        final BigDecimal rhs = expr.getRight().accept(this);
        final FloatBinaryOp op = expr.getKind();

        return switch (op) {
            case FADD -> lhs.add(rhs);
            case FSUB -> lhs.subtract(rhs);
            case FMUL -> lhs.multiply(rhs);
            case FDIV -> lhs.divide(rhs);
            case FREM -> visitBinaryExpression(expr);
        };
    }

    @Override
    public BigDecimal visitFloatUnaryExpression(FloatUnaryExpr expr) {
        final BigDecimal evaluatedExpr = expr.getOperand().accept(this);
        final FloatUnaryOp op = expr.getKind();
        return switch (op) {
            case NEG -> evaluatedExpr.negate();
        };
    }

    @Override
    public BigDecimal visitFloatLiteral(FloatLiteral lit) {
        return lit.getValue();
    }
}
