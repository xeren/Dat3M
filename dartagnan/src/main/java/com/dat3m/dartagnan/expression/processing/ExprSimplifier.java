package com.dat3m.dartagnan.expression.processing;


import com.dat3m.dartagnan.expression.BinaryExpression;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionKind;
import com.dat3m.dartagnan.expression.Type;
import com.dat3m.dartagnan.expression.aggregates.AggregateCmpExpr;
import com.dat3m.dartagnan.expression.aggregates.AggregateCmpOp;
import com.dat3m.dartagnan.expression.aggregates.ConstructExpr;
import com.dat3m.dartagnan.expression.aggregates.ExtractExpr;
import com.dat3m.dartagnan.expression.base.BinaryExpressionBase;
import com.dat3m.dartagnan.expression.base.LeafExpressionBase;
import com.dat3m.dartagnan.expression.base.LiteralExpressionBase;
import com.dat3m.dartagnan.expression.booleans.*;
import com.dat3m.dartagnan.expression.floats.FloatCmpExpr;
import com.dat3m.dartagnan.expression.floats.FloatCmpOp;
import com.dat3m.dartagnan.expression.integers.*;
import com.dat3m.dartagnan.expression.misc.ITEExpr;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.expression.utils.IntegerHelper;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExprSimplifier extends ExprTransformer {

    // If set to "false", the simplifier will not destroy register dependencies, i.e.,
    // it will maintain "expr.getRegs() == simplified(expr).getRegs()".
    // For example, "0*r" will not get simplified to "0";
    private final boolean aggressive;
    private final boolean tryGeneralRewrite;

    public ExprSimplifier(final boolean aggressive) {
        this(aggressive, true);
    }

    public ExprSimplifier(final boolean aggressive, final boolean tryGeneralRewrite) {
        this.aggressive = aggressive;
        this.tryGeneralRewrite = tryGeneralRewrite;
    }

    // Tries to perform a general rewriting of a binary expression.
    // For now, it only rewrites "ITE(cond, x, y) op z" to "ITE(cond, x op z, y op z)".
    // Returns NULL, if no rewriting is applicable.
    private Expression tryGeneralRewrite(final BinaryExpression expression) {
        if (!tryGeneralRewrite) {
            return null;
        }
        final Expression left = expression.getLeft().accept(this);
        final Expression right = expression.getRight().accept(this);
        if (!(left instanceof ITEExpr) && !(right instanceof ITEExpr)
                // We don't rewrite "ITE op ITE" for now
                || (left instanceof ITEExpr && right instanceof ITEExpr)) {
            return null;
        }

        final boolean iteIsLeft = left instanceof ITEExpr;
        final ITEExpr ite = iteIsLeft ? (ITEExpr) left : (ITEExpr) right;
        final Expression other = (iteIsLeft ? right : left);
        final ExpressionKind op = expression.getKind();

        final Expression hoistedIte = expressions.makeITE(
                ite.getCondition(),
                iteIsLeft ? expressions.makeBinary(ite.getTrueCase(), op, other) : expressions.makeBinary(other, op, ite.getTrueCase()),
                iteIsLeft ? expressions.makeBinary(ite.getFalseCase(), op, other) : expressions.makeBinary(other, op, ite.getFalseCase())
        ).accept(this);

        return hoistedIte;
    }

    @Override
    public Expression visitBoolBinaryExpression(final BoolBinaryExpr expr) {
        final Expression rewrite = tryGeneralRewrite(expr);
        if (rewrite != null) {
            return rewrite;
        }

        final Expression l = expr.getLeft().accept(this);
        final Expression r = expr.getRight().accept(this);
        final BoolBinaryOp op = expr.getKind();

        // ------- Operations with constants -------
        final boolean swap = l instanceof BoolLiteral;
        final Expression left = swap ? r : l;
        final Expression right = swap ? l : r;

        // ------- Operations on same value -------
        if (aggressive && op == BoolBinaryOp.IFF && left.equals(right)) {
            return expressions.makeTrue();
        }


        if (left instanceof final BoolLiteral l1 && right instanceof final BoolLiteral l2) {
            final boolean newValue = switch (op) {
                case AND -> l1.getValue() && l2.getValue();
                case OR -> l1.getValue() || l2.getValue();
                case IFF -> l1.getValue() == l2.getValue();
            };
            return expressions.makeValue(newValue);
        }

        final boolean isRing = switch (op) {
            case AND, OR -> true;
            default -> false;
        };
        if (right instanceof final BoolLiteral lit && isRing) {
            final boolean neutralValue = op == BoolBinaryOp.AND;
            final boolean absorbingValue = !neutralValue;

            if (lit.getValue() == neutralValue) {
                return left;
            } else if (isPotentiallyEliminable(left)) {
                return expressions.makeValue(absorbingValue);
            }
        }

        if (aggressive && (isNegation(left, right) || isNegation(right, left))) {
            return expressions.makeValue(op == BoolBinaryOp.OR);
        }

        // TODO: Simplifications of nested expressions like "b && !b => false".

        if (aggressive && op == BoolBinaryOp.AND) {
            Expression insertionExpr = checkForInsertOfEQ(left, right, false);
            if (insertionExpr == null) {
                insertionExpr = checkForInsertOfEQ(right, left, false);
            }
            if (insertionExpr != null) {
                return insertionExpr;
            }
        }

        return expressions.makeBoolBinary(left, expr.getKind(), right);
    }

    private boolean isNegation(final Expression negCandidate, final Expression expr) {
        return negCandidate instanceof final BoolUnaryExpr neg && neg.getKind() == BoolUnaryOp.NOT && neg.getOperand().equals(expr);
    }

    public Expression checkForInsertOfEQ(final Expression eq, final Expression expr, final boolean ignoreEQInResult) {
        final Expression result;
        if (eq instanceof final IntCmpExpr intCmpExpr && intCmpExpr.getKind() == IntCmpOp.EQ) {
            result = insertEQExpression(intCmpExpr, expr);
        } else if (eq instanceof final FloatCmpExpr floatCmpExpr && floatCmpExpr.getKind() == FloatCmpOp.OEQ) {
            result = insertEQExpression(floatCmpExpr, expr);
        } else if (eq.getType() instanceof BooleanType) {
            if (eq instanceof final LeafExpressionBase<?> boolValue) {
                result = boolValue.replaceIn(expr, expressions.makeTrue());
            } else if (eq instanceof final BoolUnaryExpr neg && neg.getKind() == BoolUnaryOp.NOT && neg.getOperand() instanceof final LeafExpressionBase<?> boolValue) {
                result = boolValue.replaceIn(expr, expressions. makeFalse());
            } else if (eq instanceof final BoolBinaryExpr iffExpr && iffExpr.getKind() == BoolBinaryOp.IFF) {
                result = insertEQExpression(iffExpr, expr);
            } else {
                return null;
            }
        } else {
            return null;
        }
        if (ignoreEQInResult) {
            return result;
        } else {
            return expressions.makeBoolBinary(eq, BoolBinaryOp.AND, result);
        }
    }

    private Expression insertEQExpression(final BinaryExpressionBase<?, ?> cmpExpr, final Expression exprToInsertIn) {
        final Expression firstCmpExpr = cmpExpr.getLeft();
        final Expression secondCmpExpr = cmpExpr.getRight();
        if (firstCmpExpr instanceof final LeafExpressionBase<?> exprToReplace && secondCmpExpr instanceof final LiteralExpressionBase<?> replacement) {
            return exprToReplace.replaceIn(exprToInsertIn, replacement);
        } else if (secondCmpExpr instanceof final LeafExpressionBase<?> exprToReplace && firstCmpExpr instanceof final LiteralExpressionBase<?> replacement) {
            return exprToReplace.replaceIn(exprToInsertIn, replacement);
        } else {
            return exprToInsertIn;
        }
    }

    @Override
    public Expression visitBoolUnaryExpression(final BoolUnaryExpr expr) {
        assert expr.getKind() == BoolUnaryOp.NOT;
        final Expression operand = expr.getOperand().accept(this);

        // Constant negation
        if (operand instanceof final BoolLiteral lit) {
            return expressions.makeValue(!lit.getValue());
        }

        // Double negation
        if (operand instanceof final BoolUnaryExpr negation) {
            assert negation.getKind() == BoolUnaryOp.NOT;
            return negation.getOperand();
        }

        return expressions.makeBoolUnary(expr.getKind(), operand);
    }

    @Override
    public Expression visitIntCmpExpression(final IntCmpExpr cmp) {
        final Expression rewrite = tryGeneralRewrite(cmp);
        if (rewrite != null) {
            return rewrite;
        }

        final Expression l = cmp.getLeft().accept(this);
        final Expression r = cmp.getRight().accept(this);

        // Normalize "x > y" to "y < x" (and similar).
        final boolean swap = switch (cmp.getKind()) {
            case GTE, GT, UGTE, UGT -> true;
            default -> false;
        };
        final IntCmpOp op = swap ? cmp.getKind().reverse() : cmp.getKind();
        final Expression left = swap ? r : l;
        final Expression right = swap ? l : r;

        // ------- Operations on same value -------
        if (aggressive && left.equals(right)) {
            return expressions.makeValue(!op.isStrict());
        }

        // ------- Operations with constants -------
        if (left instanceof final IntLiteral l1 && right instanceof final IntLiteral l2) {
            final int bitWidth = l1.getType().getBitWidth();
            final boolean cmpResult = switch (op) {
                case EQ -> IntegerHelper.equals(l1.getValue(), l2.getValue(), bitWidth);
                case NEQ -> !IntegerHelper.equals(l1.getValue(), l2.getValue(), bitWidth);
                case LT -> IntegerHelper.scmp(l1.getValue(), l2.getValue(), bitWidth) < 0;
                case LTE -> IntegerHelper.scmp(l1.getValue(), l2.getValue(), bitWidth) <= 0;
                case ULT -> IntegerHelper.ucmp(l1.getValue(), l2.getValue(), bitWidth) < 0;
                case ULTE -> IntegerHelper.ucmp(l1.getValue(), l2.getValue(), bitWidth) <= 0;
                default ->
                    throw new VerifyException(String.format("Unexpected comparison operator '%s'. Missing normalization?", op));
            };
            return expressions.makeValue(cmpResult);
        }

        return expressions.makeIntCmp(left, op, right);
    }

    @Override
    public Expression visitIntConcat(final IntConcat expr) {
        final List<Expression> operands = new ArrayList<>();
        boolean allLiterals = true;
        for (final Expression operand : expr.getOperands()) {
            final Expression simplifiedOperand = operand.accept(this);
            if (!(simplifiedOperand instanceof IntLiteral)) {
                allLiterals = false;
            }
            operands.add(operand);
        }

        if (allLiterals) {
            final IntLiteral firstLit = (IntLiteral) operands.get(0);
            BigInteger result = firstLit.getValue();
            int offset = firstLit.getType().getBitWidth();
            for (Expression subExpr : operands.subList(1, operands.size())) {
                final IntLiteral lit = (IntLiteral) subExpr;
                final BigInteger op = lit.getValue();
                final BigInteger offsetValue = BigInteger.TWO.pow(offset - 1);
                result = result.add(op.multiply(offsetValue));
                offset += lit.getType().getBitWidth();
            }
            return expressions.makeValue(result, expr.getType());
        }

        if (operands.size() == 1) {
            return operands.get(0);
        }
        return expressions.makeIntConcat(operands);
    }

    @Override
    public Expression visitIntExtract(IntExtract expr) {
        final Expression operand = expr.getOperand().accept(this);
        if (operand instanceof final IntLiteral lit) {
            final BigInteger op = lit.getValue();
            final BigInteger highBitValue = BigInteger.TWO.pow(expr.getHighBit() + 1);
            final BigInteger lowBitValue = BigInteger.TWO.pow(expr.getLowBit());
            final BigInteger extracted = expr.isExtractingHighBits() ? op : op.mod(highBitValue);
            final BigInteger result = expr.isExtractingLowBits() ? extracted : extracted.divide(lowBitValue);
            return expressions.makeValue(result, expr.getType());
        }

        if (expr.isExtractingHighBits() && expr.isExtractingLowBits()) {
            return operand;
        }
        return expressions.makeIntExtract(operand, expr.getLowBit(), expr.getHighBit());
    }

    @Override
    public Expression visitIntSizeCastExpression(final IntSizeCast expr) {
        final Expression operand = expr.getOperand().accept(this);

        // ------- Operations with constants -------
        if (operand instanceof final IntLiteral lit) {
            final int sourceWidth = expr.getSourceType().getBitWidth();
            final int targetWidth = expr.getTargetType().getBitWidth();
            final BigInteger newValue;
            if (expr.isExtension()) {
                newValue = IntegerHelper.extend(lit.getValue(), sourceWidth, targetWidth, expr.preservesSign());
            } else if (expr.isTruncation()) {
                newValue = IntegerHelper.truncate(lit.getValue(), targetWidth);
            } else {
                newValue = lit.getValue();
            }

            return expressions.makeValue(newValue, expr.getTargetType());
        }

        // TODO: Simplify nested casts

        // TODO: Push casts into operators?

        return expressions.makeIntegerCast(operand, expr.getTargetType(), expr.preservesSign());
    }

    @Override
    public Expression visitIntUnaryExpression(final IntUnaryExpr expr) {
        final Expression operand = expr.getOperand().accept(this);

        // ------- Operations with constants -------
        if (operand instanceof final IntLiteral lit) {
            final int bitWidth = expr.getType().getBitWidth();
            final BigInteger newValue = switch (expr.getKind()) {
                case MINUS -> IntegerHelper.neg(lit.getValue(), bitWidth);
                case CTLZ -> IntegerHelper.ctlz(lit.getValue(), bitWidth);
                case CTTZ -> IntegerHelper.cttz(lit.getValue(), bitWidth);
            };
            return expressions.makeValue(newValue, expr.getType());
        }

        // ------- Nested negation -------
        if (operand instanceof final IntUnaryExpr neg && neg.getKind() == IntUnaryOp.MINUS) {
            return neg.getOperand();
        }

        return expressions.makeIntUnary(expr.getKind(), operand);
    }

    @Override
    public Expression visitIntBinaryExpression(final IntBinaryExpr expr) {
        final Expression rewrite = tryGeneralRewrite(expr);
        if (rewrite != null) {
            return rewrite;
        }

        final Expression l = expr.getLeft().accept(this);
        final Expression r = expr.getRight().accept(this);
        final IntBinaryOp op = expr.getKind();

        // ------- Operations with constants -------
        final boolean swap = op.isCommutative() && l instanceof IntLiteral;
        final Expression left = swap ? r : l;
        final Expression right = swap ? l : r;

        // Optimizations for "x op constant"
        if (right instanceof final IntLiteral lit) {
            final boolean isZeroNeutral = lit.isZero() && switch (op) {
                case ADD, SUB, OR, XOR, LSHIFT, RSHIFT, ARSHIFT -> true;
                default -> false;
            };
            final boolean isOneNeutral = lit.isOne() && switch (op) {
                case MUL, DIV, UDIV -> true;
                default -> false;
            };
            if (isZeroNeutral || isOneNeutral) {
                return left;
            }

            if (lit.isZero() && op == IntBinaryOp.MUL && isPotentiallyEliminable(left)) {
                return expressions.makeZero(expr.getType());
            }
        }

        // Folding of constants
        if (left instanceof final IntLiteral leftLit && right instanceof final IntLiteral rightLit) {
            final int bitWidth = expr.getType().getBitWidth();
            return expressions.makeValue(op.apply(leftLit.getValue(), rightLit.getValue(), bitWidth), expr.getType());
        }

        // TODO: Use associativity to merge nested operators

        return expressions.makeIntBinary(left, op, right);
    }

    // TODO: Add simplifications for IntExtract and IntConcat expressions

    @Override
    public Expression visitITEExpression(final ITEExpr expr) {
        final Expression cond = expr.getCondition().accept(this);
        final Expression trueCase = expr.getTrueCase().accept(this);
        final Expression falseCase = expr.getFalseCase().accept(this);

        // ------- Operations with constants -------
        if (cond instanceof final BoolLiteral lit) {
            if (lit.getValue() && isPotentiallyEliminable(falseCase)) {
                return trueCase;
            }
            if (!lit.getValue() && isPotentiallyEliminable(trueCase)) {
                return falseCase;
            }
        }

        if (trueCase instanceof final BoolLiteral tLit && falseCase instanceof final BoolLiteral fLit) {
            if (tLit.getValue() == fLit.getValue()) {
                if (isPotentiallyEliminable(cond)) {
                    return tLit;
                } else if (tLit.getValue()) {
                    return expressions.makeBoolBinary(cond, BoolBinaryOp.OR, expressions.makeTrue());
                } else {
                    return expressions.makeBoolBinary(cond, BoolBinaryOp.AND, expressions.makeFalse());
                }

            }

            if (tLit.getValue()) {
                return expr.getCondition();
            } else {
                return expressions.makeNot(expr.getCondition()).accept(this);
            }

        }

        // ------- Identical cases -------
        if (aggressive && trueCase.equals(falseCase)) {
            return trueCase;
        }

        if (trueCase instanceof final BoolLiteral tLit) {
            if (tLit.getValue()) {
                return expressions.makeOr(cond, falseCase).accept(this);
            } else {
                return expressions.makeAnd(expressions.makeNot(cond), falseCase).accept(this);
            }
        } else if (falseCase instanceof final BoolLiteral fLit) {
            if (fLit.getValue()) {
                return expressions.makeOr(expressions.makeNot(cond), trueCase).accept(this);
            } else {
                return expressions.makeAnd(cond, trueCase).accept(this);
            }
        }

        return expressions.makeITE(cond, trueCase, falseCase);
    }

    @Override
    public Expression visitExtractExpression(final ExtractExpr expr) {
        Expression inner = expr.getOperand().accept(this);

        final ImmutableList<Integer> indices = expr.getIndices();
        int indexCursor = 0;
        while(indexCursor < indices.size() && (inner instanceof ConstructExpr construct)) {
            inner = construct.getOperands().get(indices.get(indexCursor));
            indexCursor++;
        }

        final ImmutableList<Integer> newIndices = indices.subList(indexCursor, indices.size());
        if (newIndices.isEmpty()) {
            return inner;
        }
        if (inner instanceof final ExtractExpr extract) {
            // Merge multiple extracts
            return expressions.makeExtract(inner, Iterables.concat(extract.getIndices(), newIndices));
        }
        return expressions.makeExtract(inner, newIndices);
    }

    // TODO: Add simplifications for InsertExpr

    @Override
    public Expression visitAggregateCmpExpression(final AggregateCmpExpr expr) {
        final Expression left = expr.getLeft().accept(this);
        final Expression right = expr.getRight().accept(this);
        assert expr.getKind() == AggregateCmpOp.EQ || expr.getKind() == AggregateCmpOp.NEQ;
        final boolean isEq = expr.getKind() == AggregateCmpOp.EQ;
        if (left instanceof final ConstructExpr l && right instanceof final ConstructExpr r) {
            final List<Expression> leftOperands = l.getOperands();
            final List<Expression> rightOperands = r.getOperands();
            final int size = leftOperands.size();
            if (size == rightOperands.size()) {
                if (leftOperands.isEmpty()) {
                    return expressions.makeValue(isEq);
                } else if (size == 1) {
                    final Expression lOp = leftOperands.get(0);
                    final Expression rOp = rightOperands.get(0);
                    return (isEq ? expressions.makeEQ(lOp, rOp) : expressions.makeNEQ(lOp, rOp)).accept(this);
                } else {
                    final List<Map.Entry<Type, BigInteger>> leftValues = new ArrayList<>();
                    for (final Expression leftOperand : leftOperands) {
                        if (leftOperand instanceof BoolLiteral bool) {
                            leftValues.add(Map.entry(leftOperand.getType(), bool.getValue() ? BigInteger.ONE : BigInteger.ZERO));
                        } else if (leftOperand instanceof IntLiteral integer) {
                            leftValues.add(Map.entry(leftOperand.getType(), integer.getValue()));
                        } else break;
                    }
                    if (leftValues.size() == size) {
                        boolean cmpResult = true;
                        int i = 0;
                        for (final Expression rightOperand : rightOperands) {
                            final Map.Entry<Type, BigInteger> leftValue = leftValues.get(i);
                            boolean eq = leftValue.getKey().equals(rightOperand.getType());
                            if (rightOperand instanceof BoolLiteral bool) {
                                eq &= (BigInteger.ONE.equals(leftValue.getValue())) == bool.getValue();
                            } else if (rightOperand instanceof IntLiteral integer) {
                                eq &= leftValue.getValue().equals(integer.getValue());
                            } else {
                                break;
                            }
                            cmpResult &= (isEq && eq) || (!isEq && !eq);
                            ++i;
                        }
                        if (i == size) {
                            return expressions.makeValue(cmpResult);
                        }
                    }
                }
            }
        }

        return expressions.makeAggregateCmp(left, expr.getKind(), right);
    }

    @Override
    public Expression visitConstructExpression(final ConstructExpr expr) {
        final List<Expression> simplifiedOperands = new ArrayList<>();
        for (final Expression operand : expr.getOperands()) {
            simplifiedOperands.add(operand.accept(this));
        }

        return expressions.makeConstruct(expr.getType(), simplifiedOperands);
    }

    // =================================== Helper methods ===================================

    // An expression is potentially eliminable if it either carries no dependencies
    // or we are in aggressive mode.
    private boolean isPotentiallyEliminable(final Expression expr) {
        return aggressive || expr.getRegs().isEmpty();
    }
}
