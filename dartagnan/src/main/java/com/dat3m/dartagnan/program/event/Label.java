package com.dat3m.dartagnan.program.event;

public class Label extends Event {

	private final String name;

	public Label() {
		this((String)null);
	}

	public Label(String name) {
		this.name = name;
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
