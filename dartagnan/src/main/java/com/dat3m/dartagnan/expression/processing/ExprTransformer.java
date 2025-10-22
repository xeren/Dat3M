package com.dat3m.dartagnan.expression.processing;

import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.ExpressionVisitor;
import com.dat3m.dartagnan.expression.LeafExpression;
import com.dat3m.dartagnan.expression.aggregates.AggregateCmpExpr;
import com.dat3m.dartagnan.expression.aggregates.ConstructExpr;
import com.dat3m.dartagnan.expression.aggregates.ExtractExpr;
import com.dat3m.dartagnan.expression.aggregates.InsertExpr;
import com.dat3m.dartagnan.expression.booleans.BoolBinaryExpr;
import com.dat3m.dartagnan.expression.booleans.BoolBinaryOp;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.booleans.BoolUnaryExpr;
import com.dat3m.dartagnan.expression.floats.FloatBinaryExpr;
import com.dat3m.dartagnan.expression.floats.FloatCmpExpr;
import com.dat3m.dartagnan.expression.floats.FloatUnaryExpr;
import com.dat3m.dartagnan.expression.integers.*;
import com.dat3m.dartagnan.expression.misc.GEPExpr;
import com.dat3m.dartagnan.expression.misc.ITEExpr;
import com.dat3m.dartagnan.expression.type.TypeFactory;

import java.util.ArrayList;

import static com.dat3m.dartagnan.expression.booleans.BoolBinaryOp.*;
import static com.dat3m.dartagnan.expression.booleans.BoolUnaryOp.NOT;

public abstract class ExprTransformer implements ExpressionVisitor<Expression> {

    protected final TypeFactory types = TypeFactory.getInstance();
    protected final ExpressionFactory expressions = ExpressionFactory.getInstance();

    @Override
    public Expression visitBoolBinaryExpression(BoolBinaryExpr expr) {
        return expressions.makeBoolBinary(expr.getLeft().accept(this), expr.getKind(), expr.getRight().accept(this));
    }

    @Override
    public Expression visitBoolUnaryExpression(BoolUnaryExpr expr) {
        return expressions.makeBoolUnary(expr.getKind(), expr.getOperand().accept(this));
    }

    @Override
    public Expression visitIntBinaryExpression(IntBinaryExpr iBin) {
        return expressions.makeIntBinary(iBin.getLeft().accept(this), iBin.getKind(), iBin.getRight().accept(this));
    }

    @Override
    public Expression visitIntCmpExpression(IntCmpExpr cmp) {
        return expressions.makeIntCmp(cmp.getLeft().accept(this), cmp.getKind(), cmp.getRight().accept(this));
    }

    @Override
    public Expression visitIntUnaryExpression(IntUnaryExpr expr) {
        return expressions.makeIntUnary(expr.getKind(), expr.getOperand().accept(this));
    }

    @Override
    public Expression visitIntConcat(IntConcat expr) {
        return expressions.makeIntConcat(expr.getOperands().stream().map(e -> e.accept(this)).toList());
    }

    @Override
    public Expression visitIntExtract(IntExtract expr) {
        return expressions.makeIntExtract(expr.getOperand().accept(this), expr.getLowBit(), expr.getHighBit());
    }

    @Override
    public Expression visitIntSizeCastExpression(IntSizeCast expr) {
        return expressions.makeIntegerCast(expr.getOperand().accept(this), expr.getTargetType(), expr.preservesSign());
    }

    @Override
    public Expression visitFloatBinaryExpression(FloatBinaryExpr expr) {
        return expressions.makeFloatBinary(expr.getLeft().accept(this), expr.getKind(), expr.getRight().accept(this));
    }

    @Override
    public Expression visitFloatCmpExpression(FloatCmpExpr expr) {
        return expressions.makeFloatCmp(expr.getLeft().accept(this), expr.getKind(), expr.getRight().accept(this));
    }

    @Override
    public Expression visitFloatUnaryExpression(FloatUnaryExpr expr) {
        return expressions.makeFloatUnary(expr.getKind(), expr.getOperand().accept(this));
    }

    @Override
    public Expression visitITEExpression(ITEExpr expr) {
        return expressions.makeITE(
                expr.getCondition().accept(this),
                expr.getTrueCase().accept(this),
                expr.getFalseCase().accept(this));
    }

    @Override
    public Expression visitConstructExpression(ConstructExpr construct) {
        final var arguments = new ArrayList<Expression>();
        for (final Expression argument : construct.getOperands()) {
            arguments.add(argument.accept(this));
        }
        return expressions.makeConstruct(construct.getType(), arguments);
    }

    @Override
    public Expression visitExtractExpression(ExtractExpr expr) {
        return expressions.makeExtract(expr.getOperand().accept(this), expr.getIndices());
    }

    @Override
    public Expression visitInsertExpression(InsertExpr insert) {
        return expressions.makeInsert(insert.getAggregate().accept(this),
                insert.getInsertedValue().accept(this), insert.getIndices());
    }

    @Override
    public Expression visitAggregateCmpExpression(AggregateCmpExpr expr) {
        return expressions.makeAggregateCmp(
                expr.getLeft().accept(this),
                expr.getKind(),
                expr.getRight().accept(this)
        );
    }

    @Override
    public Expression visitGEPExpression(GEPExpr gep) {
        Expression base = gep.getBase().accept(this);
        final var offsets = new ArrayList<Expression>();
        for (Expression offset : gep.getOffsets()) {
            offsets.add(offset.accept(this));
        }
        return expressions.makeGetElementPointer(gep.getIndexingType(), base, offsets);
    }

    @Override
    public Expression visitLeafExpression(LeafExpression expr) {
        return expr;
    }

    public static Expression negate(final Expression expr) {
        if (expr instanceof final IntCmpExpr intCmpExpr) {
            return new IntCmpExpr(intCmpExpr.getType(), intCmpExpr.getLeft(), intCmpExpr.getKind().inverted(), intCmpExpr.getRight());
        }
        if (expr instanceof final FloatCmpExpr floatCmpExpr) {
            return new FloatCmpExpr(floatCmpExpr.getType(), floatCmpExpr.getLeft(), floatCmpExpr.getKind().inverted(), floatCmpExpr.getRight());
        }
        if (expr instanceof final BoolLiteral lit) {
            return new BoolLiteral(lit.getType(), !lit.getValue());
        }
        if (expr instanceof final BoolUnaryExpr boolUnaryExpr && boolUnaryExpr.getKind() == NOT) {
            return boolUnaryExpr.getOperand();
        }
        if (expr instanceof final ITEExpr iteExpr) {
            return new ITEExpr(iteExpr.getCondition(), negate(iteExpr.getTrueCase()), negate(iteExpr.getFalseCase()));
        }
        if (expr instanceof final BoolBinaryExpr boolBinaryExpr) {
            final BoolBinaryOp op = boolBinaryExpr.getKind();
            if (op == IFF) {
                return new BoolBinaryExpr(negate(boolBinaryExpr.getLeft()), op, boolBinaryExpr.getRight());
            }
            final BoolBinaryOp newOp = op == AND ? OR : AND;
            return new BoolBinaryExpr(negate(boolBinaryExpr.getLeft()), newOp , negate(boolBinaryExpr.getRight()));
        }
        return null;
    }

    public static BoolBinaryExpr transformIffExpression(final BoolBinaryExpr expr, final BoolBinaryOp targetOp) {
        if (expr.getKind() == IFF && targetOp != IFF) {
            final Expression left = expr.getLeft();
            final Expression notLeft = negate(left);
            final Expression right = expr.getRight();
            final Expression notRight = negate(right);
            if (targetOp == OR) {
                return new BoolBinaryExpr(new BoolBinaryExpr(left, AND, right), OR, new BoolBinaryExpr(notLeft, AND, notRight));
            } else {
                return new BoolBinaryExpr(new BoolBinaryExpr(notLeft, OR, right), AND, new BoolBinaryExpr(left, OR, notRight));
            }
        }
        return expr;
    }

    public static Expression transformITEExpression(final ITEExpr expr, final BoolBinaryOp targetOp) {
        final Expression cond = expr.getCondition();
        final Expression notCond = negate(cond);
        final Expression trueCase = expr.getTrueCase();
        final Expression falseCase = expr.getFalseCase();
        if (targetOp == OR) {
            return new BoolBinaryExpr(new BoolBinaryExpr(notCond, AND, falseCase), OR, new BoolBinaryExpr(cond, AND, trueCase));
        } else if (targetOp == AND) {
            return new BoolBinaryExpr(new BoolBinaryExpr(cond, OR, falseCase), AND, new BoolBinaryExpr(notCond, OR, trueCase));
        } else {
            return expr;
        }
    }
}
