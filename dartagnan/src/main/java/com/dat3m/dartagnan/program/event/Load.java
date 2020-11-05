package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.wmm.Computation;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.Model;

public class Load extends MemEvent implements RegWriter {

	protected final Register resultRegister;

	public Load(Register register, IExpr address, String mo) {
		super(address, mo);
		this.resultRegister = register;
		addFilters(EType.ANY, EType.VISIBLE, EType.MEMORY, EType.READ, EType.REG_WRITER);
	}

	protected Load(Load other){
		super(other);
		this.resultRegister = other.resultRegister;
	}

	@Override
	public void initialise(Context ctx) {
		super.initialise(ctx);
		memValueExpr = resultRegister.toZ3IntResult(this, ctx);
	}

	@Override
	public Register getResultRegister(){
		return resultRegister;
	}

	@Override
	public IntExpr getResultRegisterExpr(){
		return memValueExpr;
	}

	@Override
	public String toString() {
		return resultRegister + " = load(*" + address + (mo != null ? ", " + mo : "") + ")";
	}

	@Override
	public String label(){
		return "R" + (mo != null ? "_" + mo : "");
	}

	@Override
	public ExprInterface getMemValue(){
		return resultRegister;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Load getCopy(){
		return new Load(this);
	}

	@Override
	public void extract(Model m, Computation.Thread t) {
		t.read(cId, resultRegister, address.getRegs());
	}
}
