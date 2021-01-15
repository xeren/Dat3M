package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.program.Register;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class Assume extends Event implements RegReaderData {

	private final ExprInterface condition;
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

    // Compilation
    // -----------------------------------------------------------------------------------------------------------------

	@Override
	public void encode(Context c, RuleAcceptor out, BoolExpr in) {
		super.encode(c, out, in);
		out.add(c.mkEq(execVar, condition.toZ3Bool(this, c)));
	}
}
