package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.IExpr;

/**
Donates a value readable from address.
*/
public abstract class InitOrStore extends MemEvent {

	protected InitOrStore(IExpr address) {
		super(address);
	}

	protected InitOrStore(MemEvent other) {
		super(other);
	}
}
