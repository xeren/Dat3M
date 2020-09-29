package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.op.BOpUn;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import java.util.function.Consumer;

public class BExprUn extends BExpr {

	private final ExprInterface b;
	private final BOpUn op;

	public BExprUn(BOpUn op, ExprInterface b) {
		this.b = b;
		this.op = op;
	}

	@Override
	public BoolExpr toZ3Bool(Event e, EncodeContext c) {
		return op.encode(b.toZ3Bool(e, c), c.context);
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		BoolExpr expr = c.lt(c.one(), b.getLastValueExpr(c));
		return (IntExpr) c.context.mkITE(op.encode(expr, c.context), c.one(), c.zero());
	}

	@Override
	public void subexpression(Consumer<ExprInterface> a) {
		a.accept(b);
	}

	@Override
	public String toString() {
		return "(" + op + " " + b + ")";
	}

	@Override
	public boolean getBoolValue(Event e, EncodeContext ctx, Model model) {
		return op.combine(b.getBoolValue(e, ctx, model));
	}

	@Override
	public IConst reduce() {
		return new IConst(b.reduce().getValue());
	}
}
