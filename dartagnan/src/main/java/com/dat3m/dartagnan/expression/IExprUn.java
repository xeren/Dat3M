package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.expression.op.IOpUn;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class IExprUn extends IExpr {

	private final ExprInterface b;
	private final IOpUn op;

	public IExprUn(IOpUn op, ExprInterface b) {
		this.b = b;
		this.op = op;
	}

	@Override
	public IntExpr toZ3Int(Event e, EncodeContext c) {
		return op.encode(b.toZ3Int(e, c), c.context);
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		return op.encode(b.getLastValueExpr(c), c.context);
	}

	@Override
	public int getIntValue(Event e, EncodeContext c, Model m) {
		return -(b.getIntValue(e, c, m));
	}

	@Override
	public ImmutableSet<Register> getRegs() {
		return new ImmutableSet.Builder<Register>().addAll(b.getRegs()).build();
	}

	@Override
	public String toString() {
		return "(" + op + b + ")";
	}

	@Override
	public IConst reduce() {
		switch(op) {
			case MINUS:
				return new IConst(-b.reduce().getValue());
			default:
				throw new UnsupportedOperationException("Reduce not supported for " + this);
		}
	}
}
