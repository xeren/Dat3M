package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.program.utils.EType;

public class BoundEvent extends Event {

	public BoundEvent() {
		super();
		addFilters(EType.ANY);
	}

	protected BoundEvent(BoundEvent other) {
		super(other);
	}

	@Override
	public BoundEvent getCopy() {
		return new BoundEvent(this);
	}
}
