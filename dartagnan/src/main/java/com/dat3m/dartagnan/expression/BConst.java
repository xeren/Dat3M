package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.utils.Encoder;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class BConst extends BExpr implements ExprInterface {

	private final boolean value;
	
	public BConst(boolean value) {
		this.value = value;
	}

    @Override
	public BoolExpr toZ3Bool(Event e, Encoder ctx) {
		return value ? ctx.mkTrue() : ctx.mkFalse();
	}

	@Override
	public IntExpr getLastValueExpr(Encoder ctx){
		return value ? ctx.mkInt(1) : ctx.mkInt(0);
	}

    @Override
	public ImmutableSet<Register> getRegs() {
		return ImmutableSet.of();
	}

	@Override
	public String toString() {
		return value ? "True" : "False";
	}

	@Override
	public boolean getBoolValue(Event e, Encoder ctx, Model model){
		return value;
	}

	@Override
	public IConst reduce() {
		return new IConst(value ? 1 : 0);
	}
	
	public boolean getValue() {
		return value;
	}
}
