package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.program.event.Event;

public abstract class IExpr implements ExprInterface {

	@Override
	public BoolExpr toZ3Bool(Event e, EncodeContext c) {
		return c.lt(c.zero(), toZ3Int(e, c));
	}

	@Override
	public boolean getBoolValue(Event e, EncodeContext c, Model m) {
		return getIntValue(e, c, m) > 0;
	}
}
