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
		return c.and(e.exec(), c.eq(c.zero(), e.getResultRegisterExpr()));
	}

	@Override
	public String toString() {
		return "!" + e.getResultRegister();
	}
}
