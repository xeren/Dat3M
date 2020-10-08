package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.program.utils.EType;

import java.util.HashSet;
import java.util.Set;

public class Label extends Event {

	private final String name;
	final Set<Event> listeners = new HashSet<>();

	public Label(String name) {
		this.name = name;
		addFilters(EType.ANY, EType.LABEL);
	}

	protected Label(Label other) {
		super(other);
		this.name = other.name;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name + ":";
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Label getCopy() {
		Label copy = new Label(this);
		for(Event jump: listeners) {
			jump.notify(copy);
		}
		return copy;
	}
}
