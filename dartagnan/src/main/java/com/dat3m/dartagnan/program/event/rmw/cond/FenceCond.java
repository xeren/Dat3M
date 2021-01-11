package com.dat3m.dartagnan.program.event.rmw.cond;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Fence;

public class FenceCond extends Fence {

	private final RMWReadCond loadEvent;

	public FenceCond(RMWReadCond loadEvent, String name) {
		super(name);
		this.loadEvent = loadEvent;
	}

	@Override
	public void encode(Context c, RuleAcceptor out, BoolExpr in) {
		super.encode(c, out, in);
		execVar = c.mkBoolConst("exec " + cId);
		out.add(c.mkEq(execVar, c.mkAnd(cfVar, loadEvent.getCond())));
	}

	@Override
	public String toString() {
		return String.format("%1$-" + Event.PRINT_PAD_EXTRA + "s", super.toString()) + loadEvent.condToString();
	}
}
