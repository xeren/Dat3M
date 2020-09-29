package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

public class IConst extends IExpr implements ExprInterface {

	private final int value;

	public IConst(int value) {
		this.value = value;
	}

	@Override
	public IntExpr toZ3Int(Event e, EncodeContext c) {
		return c.context.mkInt(value);
	}

	@Override
	public String toString() {
		return Integer.toString(value);
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		return c.context.mkInt(value);
	}

	@Override
	public int getIntValue(Event e, EncodeContext c, Model m) {
		return value;
	}

	public IntExpr toZ3Int(EncodeContext c) {
		return c.context.mkInt(value);
	}

	@Override
	public IConst reduce() {
		return this;
	}

	public int getValue() {
		return value;
	}
}
