package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.program.utils.EType;

public class Fence extends Event {

	public Fence(String... tag) {
		addFilters(EType.ANY, EType.VISIBLE, EType.FENCE);
		addFilters(tag);
	}

	protected Fence(Fence other) {
		super(other);
	}

	@Override
	public String toString() {
		return filter.toString();
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Fence getCopy() {
		return new Fence(this);
	}
}
