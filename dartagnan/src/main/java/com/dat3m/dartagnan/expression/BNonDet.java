package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.utils.Encoder;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.google.common.collect.ImmutableSet;

public class BNonDet extends BExpr implements ExprInterface {

	@Override
	public IConst reduce() {
        throw new UnsupportedOperationException("Reduce not supported for " + this);
	}

	@Override
	public BoolExpr toZ3Bool(Event e, Encoder ctx) {
		return ctx.someBoolean();
	}

	@Override
	public IntExpr getLastValueExpr(Encoder ctx) {
		throw new UnsupportedOperationException("getLastValueExpr not supported for " + this);
	}

	@Override
	public boolean getBoolValue(Event e, Encoder ctx, Model model) {
		return model.getConstInterp(toZ3Int(e, ctx)).isTrue();
	}

	@Override
	public ImmutableSet<Register> getRegs() {
		return ImmutableSet.of();
	}

	@Override
	public String toString() {
		return "nondet_bool()";
	}
}
