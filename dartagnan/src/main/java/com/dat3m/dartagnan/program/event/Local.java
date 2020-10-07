package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.IntExpr;
import java.util.Set;

public class Local extends Event implements RegWriter, RegReaderData {

	protected final Register register;
	protected final ExprInterface expr;
	private final Set<Register> dataRegs;

	public Local(Register register, ExprInterface expr) {
		this.register = register;
		this.expr = expr;
		this.dataRegs = Register.of(expr);
		addFilters(EType.ANY, EType.LOCAL, EType.REG_WRITER, EType.REG_READER);
	}

	protected Local(Local other) {
		super(other);
		this.register = other.register;
		this.expr = other.expr;
		this.dataRegs = other.dataRegs;
	}

	public ExprInterface getExpr() {
		return expr;
	}

	@Override
	public Register getResultRegister() {
		return register;
	}

	@Override
	public IntExpr getResultRegisterExpr(EncodeContext e) {
		return register.toZ3IntResult(this, e);
	}

	@Override
	public Set<Register> getDataRegs() {
		return dataRegs;
	}

	@Override
	public String toString() {
		return register + " <- " + expr;
	}

	@Override
	protected void encodeExec(EncodeContext e) {
		super.encodeExec(e);
		e.rule(e.eq(register.toZ3IntResult(this, e), expr.toZ3Int(this, e)));
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Local getCopy() {
		return new Local(this);
	}
}