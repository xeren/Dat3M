package com.dat3m.dartagnan.program.event;

public class FunCall extends Event {

	String funName;

	public FunCall(String funName) {
		this.funName = funName;
	}

	protected FunCall(FunCall other) {
		super(other);
		this.funName = other.funName;
	}

	@Override
	protected String label() {
		return " " + funName + " ";
	}

	public String getFunctionName() {
		return funName;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public FunCall getCopy() {
		return new FunCall(this);
	}
}
