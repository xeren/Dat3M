package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import java.util.function.Consumer;

public class IExprBin extends IExpr implements ExprInterface {

	private final ExprInterface lhs;
	private final ExprInterface rhs;
	private final IOpBin op;

	public IExprBin(ExprInterface lhs, IOpBin op, ExprInterface rhs) {
		this.lhs = lhs;
		this.rhs = rhs;
		this.op = op;
	}

	@Override
	public IntExpr toZ3Int(Event e, EncodeContext c) {
		return op.encode(lhs.toZ3Int(e, c), rhs.toZ3Int(e, c), c.context);
	}

	@Override
	public BoolExpr toZ3Bool(Event e, EncodeContext c) {
		return c.lt(c.zero(), toZ3Int(e, c));
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		return op.encode(lhs.getLastValueExpr(c), rhs.getLastValueExpr(c), c.context);
	}

	@Override
	public void subexpression(Consumer<ExprInterface> a) {
		a.accept(lhs);
		a.accept(rhs);
	}

	@Override
	public String toString() {
		return "(" + lhs + " " + op + " " + rhs + ")";
	}

	@Override
	public int getIntValue(Event e, EncodeContext c, Model m) {
		return op.combine(lhs.getIntValue(e, c, m), rhs.getIntValue(e, c, m));
	}

	@Override
	public IConst reduce() {
		int v1 = lhs.reduce().getValue();
		int v2 = rhs.reduce().getValue();
		switch(op) {
			case PLUS:
				return new IConst(v1 + v2);
			case MINUS:
				return new IConst(v1 - v2);
			case MULT:
				return new IConst(v1 * v2);
			case DIV:
				return new IConst(v1 / v2);
			case MOD:
				return new IConst(v1 % v2);
			case AND:
				return new IConst(v1 == 1 ? v2 : 0);
			case OR:
				return new IConst(v1 == 1 ? 1 : v2);
			case XOR:
				return new IConst(v1 + v2 == 1 ? 1 : 0);
			default:
				throw new UnsupportedOperationException("Reduce not supported for " + this);
		}
	}
}
