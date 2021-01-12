package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.program.utils.EType;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.INonDet;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;

public class Local extends Event implements RegWriter, RegReaderData {
	
	protected final Register register;
	protected final ExprInterface expr;
	private final ImmutableSet<Register> dataRegs;
	private Expr regResultExpr;
	
	public Local(Register register, ExprInterface expr) {
		this.register = register;
		this.expr = expr;
		this.dataRegs = expr.getRegs();
		addFilters(EType.ANY, EType.LOCAL);
	}

	protected Local(Local other){
		super(other);
		this.register = other.register;
		this.expr = other.expr;
		this.dataRegs = other.dataRegs;
		this.regResultExpr = other.regResultExpr;
	}

	@Override
	public void encode(Context c, RuleAcceptor out, BoolExpr in) {
		super.encode(c, out, in);
		regResultExpr = register.toZ3IntResult(this, c);
		Expr x = expr.toZ3Int(this, c);
		out.add(c.mkEq(regResultExpr, x));
		if(expr instanceof INonDet)
			out.add(((INonDet)expr).encodeBounds(x.isBV(), c));
	}

	public ExprInterface getExpr(){
		return expr;
	}

	@Override
	public Register getResultRegister(){
		return register;
	}

	@Override
	public Expr getResultRegisterExpr(){
		return regResultExpr;
	}

	@Override
	public ImmutableSet<Register> getDataRegs(){
		return dataRegs;
	}

    @Override
	public String toString() {
		return register + " <- " + expr;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Local getCopy(){
		return new Local(this);
	}
}