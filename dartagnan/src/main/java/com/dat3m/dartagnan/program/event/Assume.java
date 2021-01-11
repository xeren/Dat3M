package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class Assume extends Event implements RegReaderData {

	private final ExprInterface condition;
	private final ImmutableSet<Register> registers;

	public Assume(ExprInterface e) {
		condition = e;
		registers = e.getRegs();
        addFilters(EType.ANY, EType.REG_READER, EType.JUMP);
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
    public String toString() {
        return "assume(" + condition + ")";
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
		out.add(c.mkEq(cfVar, condition.toZ3Bool(this, c)));
	}
}
