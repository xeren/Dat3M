package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.expression.op.COpBin;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import java.util.function.Consumer;

public class Atom extends BExpr implements ExprInterface {

	private final ExprInterface lhs;
	private final ExprInterface rhs;
	private final COpBin op;

	public Atom(ExprInterface lhs, COpBin op, ExprInterface rhs) {
		this.lhs = lhs;
		this.rhs = rhs;
		this.op = op;
	}

	@Override
	public BoolExpr toZ3Bool(Event e, EncodeContext c) {
		return op.encode(lhs.toZ3Int(e, c), rhs.toZ3Int(e, c), c.context);
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		return (IntExpr) c.context.mkITE(
			op.encode(lhs.getLastValueExpr(c), rhs.getLastValueExpr(c), c.context),
			c.one(),
			c.zero()
		);
	}

	@Override
	public void subexpression(Consumer<ExprInterface> a) {
		a.accept(lhs);
		a.accept(rhs);
	}

	@Override
	public String toString() {
		return lhs + " " + op + " " + rhs;
	}

	@Override
	public boolean getBoolValue(Event e, EncodeContext ctx, Model model) {
		return op.combine(lhs.getIntValue(e, ctx, model), rhs.getIntValue(e, ctx, model));
	}

	public COpBin getOp() {
		return op;
	}

	public ExprInterface getLHS() {
		return lhs;
	}

	public ExprInterface getRHS() {
		return rhs;
	}

	@Override
	public IConst reduce() {
		int v1 = lhs.reduce().getValue();
		int v2 = lhs.reduce().getValue();
		switch(op) {
			case EQ:
				return new IConst(v1 == v2 ? 1 : 0);
			case NEQ:
				return new IConst(v1 != v2 ? 1 : 0);
			case LT:
				return new IConst(v1 < v2 ? 1 : 0);
			case LTE:
				return new IConst(v1 <= v2 ? 1 : 0);
			case GT:
				return new IConst(v1 > v2 ? 1 : 0);
			case GTE:
				return new IConst(v1 >= v2 ? 1 : 0);
		}
		throw new UnsupportedOperationException("Reduce not supported for " + this);
	}
}