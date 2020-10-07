package com.dat3m.dartagnan.program.event.rmw.cond;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.rmw.RMWStore;

/**
 * Optional store event in an atomic RMW command.
 * Is not executed unless the attached condition is satisfied.
 * Unconditionally appears in the execution's control flow.
 */
public class RMWStoreCond extends RMWStore {

	public RMWStoreCond(RMWReadCond loadEvent, IExpr address, ExprInterface value, String mo) {
		super(loadEvent, address, value, mo);
	}

	@Override
	public String toString() {
		return String.format("%1$-" + Event.PRINT_PAD_EXTRA + "s", super.toString()) + ((RMWReadCond) loadEvent).condToString();
	}

	@Override
	protected void encodeExec(EncodeContext e) {
		e.rule(e.eq(e.exec(this), e.and(e.cf(this), ((RMWReadCond) loadEvent).getCond(e))));
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void unroll(int bound, Event predecessor) {
		throw new RuntimeException("RMWStoreCond cannot be unrolled: event must be generated during compilation");
	}
}
