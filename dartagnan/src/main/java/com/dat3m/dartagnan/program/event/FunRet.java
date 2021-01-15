package com.dat3m.dartagnan.program.event;

public class FunRet extends Event {

	String funName;

	public FunRet(String funName) {
		this.funName = funName;
	}

	protected FunRet(FunRet other) {
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
	public FunRet getCopy() {
		return new FunRet(this);
	}
}
