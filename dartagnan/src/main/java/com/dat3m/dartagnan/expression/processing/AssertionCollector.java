package com.dat3m.dartagnan.expression.processing;

import java.util.ArrayList;
import java.util.List;

import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.base.ExpressionBase;
import com.dat3m.dartagnan.expression.booleans.BoolBinaryExpr;
import com.dat3m.dartagnan.expression.booleans.BoolBinaryOp;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.booleans.BoolUnaryExpr;
import com.dat3m.dartagnan.expression.floats.FloatCmpExpr;
import com.dat3m.dartagnan.expression.integers.IntCmpExpr;
import com.dat3m.dartagnan.expression.misc.ITEExpr;
import com.dat3m.dartagnan.expression.type.BooleanType;

import static com.dat3m.dartagnan.expression.booleans.BoolBinaryOp.AND;
import static com.dat3m.dartagnan.expression.booleans.BoolBinaryOp.OR;
import static com.dat3m.dartagnan.expression.booleans.BoolUnaryOp.NOT;

public class AssertionCollector implements ExpressionVisitor<List<ExpressionBase<BooleanType>>> {
    
    private BoolBinaryOp collectionOperator = null;

    public BoolBinaryOp getOperator() {
        return collectionOperator;
    }

    private List<ExpressionBase<BooleanType>> visitLeafExpression(ExpressionBase<BooleanType> expr) {
        List<ExpressionBase<BooleanType>> assertions = new ArrayList<>();
        assertions.add(expr);
        return assertions;
    }

    @Override
    public List<ExpressionBase<BooleanType>> visitIntCmpExpression(IntCmpExpr expr) {
        return visitLeafExpression(expr);
    }

    @Override
    public List<ExpressionBase<BooleanType>> visitBoolBinaryExpression(BoolBinaryExpr expr) {
        if (expr.getKind() == BoolBinaryOp.IFF) {
            return transformIffExpression(expr).accept(this);
        }
        if (collectionOperator == null) {
            collectionOperator = expr.getKind();
        }
        if (collectionOperator != expr.getKind()) {
            return visitLeafExpression(expr);
        }
        List<ExpressionBase<BooleanType>> assertions = expr.getLeft().accept(this);
        assertions.addAll(expr.getRight().accept(this));
        return assertions;
    }

    private Expression transformIffExpression(BoolBinaryExpr expr) {
        if (expr.getKind() == BoolBinaryOp.IFF) {
            Expression left = expr.getLeft();
            Expression notLeft = negate(left);
            Expression right = expr.getRight();
            Expression notRight = negate(right);
            if (collectionOperator == OR) {
                return new BoolBinaryExpr(new BoolBinaryExpr(left, AND, right), OR, new BoolBinaryExpr(notLeft, AND, notRight));
            } else {
                return new BoolBinaryExpr(new BoolBinaryExpr(notLeft, OR, right), AND, new BoolBinaryExpr(left, OR, notRight));
            }
        }
        return null;
    }

    @Override
    public List<ExpressionBase<BooleanType>> visitBoolUnaryExpression(BoolUnaryExpr expr) {
        Expression negation;
        if (expr.getKind() != NOT || (negation = negate(expr.getOperand())) == null) {
            return visitUnaryExpression(expr);
        }
        return negation.accept(this);
    }

    public static Expression negate(Expression expr) {
        if (expr instanceof IntCmpExpr intCmpExpr) {
            return new IntCmpExpr(intCmpExpr.getType(), intCmpExpr.getLeft(), intCmpExpr.getKind().inverted(), intCmpExpr.getRight());
        }
        if (expr instanceof FloatCmpExpr floatCmpExpr) {
            return new FloatCmpExpr(floatCmpExpr.getType(), floatCmpExpr.getLeft(), floatCmpExpr.getKind().inverted(), floatCmpExpr.getRight());
        }
        if (expr instanceof BoolLiteral lit) {
            return new BoolLiteral(lit.getType(), !lit.getValue());
        }
        if (expr instanceof BoolUnaryExpr boolUnaryExpr && boolUnaryExpr.getKind() == NOT) {
            return boolUnaryExpr.getOperand();
        }
        if (expr instanceof ITEExpr iteExpr) {
            return new ITEExpr(iteExpr.getCondition(), negate(iteExpr.getTrueCase()), negate(iteExpr.getFalseCase()));
        }
        if (expr instanceof BoolBinaryExpr boolBinaryExpr) {
            BoolBinaryOp op = boolBinaryExpr.getKind();
            if (op == BoolBinaryOp.IFF) {
                return new BoolBinaryExpr(negate(boolBinaryExpr.getLeft()), op, boolBinaryExpr.getRight());
            }
            BoolBinaryOp newOp = op == AND ? OR : AND;
            return new BoolBinaryExpr(negate(boolBinaryExpr.getLeft()), newOp , negate(boolBinaryExpr.getRight()));
        }
        return null;
    }

    @Override
    public List<ExpressionBase<BooleanType>> visitBoolLiteral(BoolLiteral lit) {
        if ((collectionOperator == OR && lit.getValue()) || (collectionOperator == AND && !lit.getValue())) {
            return visitLeafExpression((ExpressionBase<BooleanType>) lit);
        }
        return new ArrayList<>();
    }

    @Override
    public List<ExpressionBase<BooleanType>> visitFloatCmpExpression(FloatCmpExpr expr) {
        return visitLeafExpression(expr);
    }

    @Override
    public List<ExpressionBase<BooleanType>> visitITEExpression(ITEExpr expr) {
        if (!(expr.getType() instanceof BooleanType)) {
            return visitExpression(expr);
        }
        return transformITEExpression(expr).accept(this);
    }

    private Expression transformITEExpression(ITEExpr expr) {
        Expression cond = expr.getCondition();
        Expression notCond = negate(cond);
        Expression trueCase = expr.getTrueCase();
        Expression falseCase = expr.getFalseCase();
        if (cond instanceof BoolLiteral lit) {
            return lit.getValue() ? trueCase : falseCase;
        } else if (trueCase instanceof BoolLiteral lit) {
            if (lit.getValue()) {
                return new BoolBinaryExpr(cond, OR, falseCase);
            } else {
                return new BoolBinaryExpr(notCond, AND, falseCase);
            }
        } else if (falseCase instanceof BoolLiteral lit) {
            if (lit.getValue()) {
                return new BoolBinaryExpr(notCond, OR, trueCase);
            } else {
                return new BoolBinaryExpr(cond, AND, trueCase);
            }
        } else if (collectionOperator == OR) {
            return new BoolBinaryExpr(new BoolBinaryExpr(notCond, AND, falseCase), OR, new BoolBinaryExpr(cond, AND, trueCase));
        } else {
            return new BoolBinaryExpr(new BoolBinaryExpr(cond, OR, falseCase), AND, new BoolBinaryExpr(notCond, OR, trueCase));
        }
    }
}
