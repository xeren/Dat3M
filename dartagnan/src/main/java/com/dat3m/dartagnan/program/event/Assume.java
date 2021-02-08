package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.program.Register;
import com.google.common.collect.ImmutableSet;

public class Assume extends Event implements RegReaderData {

	public final ExprInterface condition;
	private final ImmutableSet<Register> registers;

	public Assume(ExprInterface e) {
		condition = e;
		registers = e.getRegs();
	}

	protected Assume(Assume other) {
		super(other);
		condition = other.condition;
		registers = other.registers;
	}

	@Override
	public ImmutableSet<Register> getDataRegs() {
		return registers;
	}

	@Override
    protected String label() {
        return " " + condition.toString() + " ";
    }

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Assume getCopy() {
		return new Assume(this);
	}
}
