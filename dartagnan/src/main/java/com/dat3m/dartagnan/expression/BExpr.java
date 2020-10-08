package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

public abstract class BExpr implements ExprInterface {

	@Override
	public IntExpr toZ3Int(Event e, EncodeContext c) {
		return (IntExpr) c.context.mkITE(toZ3Bool(e, c), c.one(), c.zero());
	}

	@Override
	public int getIntValue(Event e, EncodeContext c, Model m) {
		return getBoolValue(e, c, m) ? 1 : 0;
	}
}
