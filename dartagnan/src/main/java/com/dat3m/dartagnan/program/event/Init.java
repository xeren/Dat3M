package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class Init extends InitOrStore {

	private final IConst value;

	public Init(Address address, IConst value) {
		super(address);
		this.value = value;
		addFilters(EType.VISIBLE);
	}

	private Init(Init other) {
		super(other);
		this.value = other.value;
	}

	public IConst getValue() {
		return value;
	}

	@Override
	public void encode(Context c, RuleAcceptor out, BoolExpr in) {
		super.encode(c, out, in);
		memValueExpr = value.toZ3Int(c);
	}

	@Override
	protected String label() {
		return " " + address + " " + value + " ";
	}

	@Override
	public IConst getMemValue() {
		return value;
	}


	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Init getCopy() {
		return new Init(this);
	}
}