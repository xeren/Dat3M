package com.dat3m.dartagnan.asserts;

import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.BoolExpr;

import com.dat3m.dartagnan.program.event.Local;

public class AssertInline extends AbstractAssert {

	private final Local e;

	public AssertInline(Local e) {
		this.e = e;
	}

	@Override
	public BoolExpr encode(EncodeContext c) {
		return c.and(c.exec(e), c.eq(c.zero(), e.getResultRegisterExpr(c)));
	}

	@Override
	public String toString() {
		return "!" + e.getResultRegister();
	}
}
