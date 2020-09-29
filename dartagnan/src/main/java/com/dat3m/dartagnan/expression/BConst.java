package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

public class BConst extends BExpr implements ExprInterface {

	private final boolean value;

	public BConst(boolean value) {
		this.value = value;
	}

	@Override
	public BoolExpr toZ3Bool(Event e, EncodeContext c) {
		return value ? c.and() : c.or();
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		return value ? c.one() : c.zero();
	}

	@Override
	public String toString() {
		return value ? "True" : "False";
	}

	@Override
	public boolean getBoolValue(Event e, EncodeContext c, Model m) {
		return value;
	}

	@Override
	public IConst reduce() {
		return new IConst(value ? 1 : 0);
	}

	public boolean getValue() {
		return value;
	}
}
