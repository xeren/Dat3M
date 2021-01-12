package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class Store extends MemEvent implements RegReaderData {

	protected final ExprInterface value;
	private final ImmutableSet<Register> dataRegs;

	public Store(IExpr address, ExprInterface value, String... tag) {
		super(address);
		this.value = value;
		dataRegs = value.getRegs();
		addFilters(EType.ANY, EType.VISIBLE, EType.MEMORY, EType.WRITE);
		addFilters(tag);
	}

	protected Store(Store other) {
		super(other);
		this.value = other.value;
		dataRegs = other.dataRegs;
	}

	@Override
	public void encode(Context c, RuleAcceptor out, BoolExpr in) {
		super.encode(c, out, in);
		memValueExpr = value.toZ3Int(this, c);
	}

	@Override
	public ImmutableSet<Register> getDataRegs() {
		return dataRegs;
	}

	@Override
	public String toString() {
		return "store(*" + address + ", " + value + ")";
	}

	@Override
	public ExprInterface getMemValue() {
		return value;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Store getCopy() {
		return new Store(this);
	}
}
