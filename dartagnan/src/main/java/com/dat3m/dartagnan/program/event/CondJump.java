package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.BConst;
import com.dat3m.dartagnan.expression.BExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.google.common.collect.ImmutableSet;

public class CondJump extends Event implements RegReaderData {

	private final Label label;
	private final BExpr expr;
	private final ImmutableSet<Register> dataRegs;

	public CondJump(BExpr expr, Label label) {
		if(label == null) {
			throw new IllegalArgumentException("CondJump event requires non null label event");
		}
		if(expr == null) {
			throw new IllegalArgumentException("CondJump event requires non null expression");
		}
		this.label = label;
		this.expr = expr;
		dataRegs = expr.getRegs();
		addFilters(EType.ANY);
	}

	/**
	Called during unrolling of a program.
	Creates a copy of th
	*/
	public CondJump(CondJump other, Label label) {
		super(other);
		expr = other.expr;
		dataRegs = other.dataRegs;
		this.label = label;
	}

	public BExpr getCondition() {
		return expr;
	}

	/**
	Unconditional jumps have a literal true condition.
	@return
	Syntactically there is no run that does not jump.
	*/
	public boolean isUnconditional() {
		return expr instanceof BConst && ((BConst) expr).getValue();
	}

	public Label getLabel() {
		return label;
	}

	@Override
	public ImmutableSet<Register> getDataRegs() {
		return dataRegs;
	}

	@Override
	public String toString() {
		if(isUnconditional()) {
			return "goto " + label;
		}
		return "if(" + expr + "); then goto " + label;
	}
}
