package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

public class BNonDet extends BExpr implements ExprInterface {

	@Override
	public IConst reduce() {
		throw new UnsupportedOperationException("Reduce not supported for " + this);
	}

	@Override
	public BoolExpr toZ3Bool(Event e, EncodeContext c) {
		return c.context.mkBoolConst(Integer.toString(hashCode()));
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		throw new UnsupportedOperationException("getLastValueExpr not supported for " + this);
	}

	@Override
	public boolean getBoolValue(Event e, EncodeContext c, Model m) {
		return m.getConstInterp(toZ3Int(e, c)).isTrue();
	}

	@Override
	public String toString() {
		return "nondet_bool()";
	}
}
