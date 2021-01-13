package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.program.utils.EType;

public class Label extends Event {

	private final String name;

	public Label() {
		this((String)null);
	}

	public Label(String name) {
		this.name = name;
		addFilters(EType.ANY);
	}

	protected Label(Label other) {
		super(other);
		this.name = other.name;
	}

	public String getName() {
		return name;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Label getCopy() {
		return new Label(this);
	}
}
