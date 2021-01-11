package com.dat3m.dartagnan.program.event.rmw.cond;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class RMWReadCondUnless extends RMWReadCond implements RegWriter, RegReaderData {

	public RMWReadCondUnless(Register reg, ExprInterface cmp, IExpr address, String mo) {
		super(reg, cmp, address, mo);
	}

	@Override
	public void encode(Context c, RuleAcceptor out, BoolExpr in) {
		super.encode(c, out, in);
		z3Cond = c.mkNot(z3Cond);
	}

	@Override
	public String condToString() {
		return "# if not " + resultRegister + " = " + cmp;
	}
}
