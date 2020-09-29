package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.op.BOpBin;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import java.util.function.Consumer;

public class BExprBin extends BExpr {

	private final ExprInterface b1;
	private final ExprInterface b2;
	private final BOpBin op;

	public BExprBin(ExprInterface b1, BOpBin op, ExprInterface b2) {
		this.b1 = b1;
		this.b2 = b2;
		this.op = op;
	}

	@Override
	public BoolExpr toZ3Bool(Event e, EncodeContext c) {
		return op.encode(b1.toZ3Bool(e, c), b2.toZ3Bool(e, c), c.context);
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		BoolExpr expr1 = c.lt(c.one(), b1.getLastValueExpr(c));
		BoolExpr expr2 = c.lt(c.one(), b2.getLastValueExpr(c));
		return (IntExpr) c.context.mkITE(op.encode(expr1, expr2, c.context), c.one(), c.zero());
	}

	@Override
	public void subexpression(Consumer<ExprInterface> a) {
		a.accept(b1);
		a.accept(b2);
	}

	@Override
	public String toString() {
		return "(" + b1 + " " + op + " " + b2 + ")";
	}

	@Override
	public boolean getBoolValue(Event e, EncodeContext ctx, Model model) {
		return op.combine(b1.getBoolValue(e, ctx, model), b2.getBoolValue(e, ctx, model));
	}

	@Override
	public IConst reduce() {
		int v1 = b1.reduce().getValue();
		int v2 = b2.reduce().getValue();
		switch(op) {
			case AND:
				return new IConst(v1 == 1 ? v2 : 0);
			case OR:
				return new IConst(v1 == 1 ? 1 : v2);
		}
		throw new UnsupportedOperationException("Reduce not supported for " + this);
	}
}
