package com.dat3m.dartagnan.program.event;

public class BoundEvent extends Event {

	public BoundEvent() {
		super();
	}

	protected BoundEvent(BoundEvent other) {
		super(other);
	}

	@Override
	public BoundEvent getCopy() {
		return new BoundEvent(this);
	}
}
