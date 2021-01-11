package com.dat3m.dartagnan.program.event.rmw.cond;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.rmw.RMWStore;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;

public class RMWStoreCond extends RMWStore implements RegReaderData {

	public RMWStoreCond(RMWReadCond loadEvent, IExpr address, ExprInterface value, String mo) {
		super(loadEvent, address, value, mo);
	}

	@Override
	public String toString() {
		return String.format("%1$-" + Event.PRINT_PAD_EXTRA + "s", super.toString()) + ((RMWReadCond) loadEvent).condToString();
	}

	@Override
	public void encode(Context c, RuleAcceptor out, BoolExpr in) {
		super.encode(c, out, in);
		execVar = c.mkBoolConst("exec " + cId);
		out.add(c.mkEq(execVar, c.mkAnd(cfVar, ((RMWReadCond) loadEvent).getCond())));
	}
}
