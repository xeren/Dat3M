package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.BoolExpr;

public class Jump extends Event {

	protected Label label;
	protected Label label4Copy;

	public Jump(Label label) {
		if(label == null)
			throw new IllegalArgumentException("Jump event requires non null label event");
		this.label = label;
		this.label.listeners.add(this);
		addFilters(EType.ANY, EType.JUMP);
	}

	protected Jump(Jump other) {
		super(other);
		label = other.label4Copy;
		(label != null ? label : other.label).listeners.add(this);
	}

	public Label getLabel() {
		return label;
	}

	@Override
	public String toString() {
		return "goto " + label;
	}

	@Override
	public void notify(Event label) {
		if(this.label == null) {
			this.label = (Label) label;
		} else if(oId > label.getOId()) {
			this.label4Copy = (Label) label;
		}
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void unroll(int bound, Event predecessor) {
		if(label.getOId() < oId) {
			if(bound > 1) {
				predecessor = copyPath(label, successor, predecessor);
			}
			Event next = predecessor;
			if(bound == 1) {
				next = new BoundEvent();
				predecessor.setSuccessor(next);
			}
			if(successor != null) {
				successor.unroll(bound, next);
			}
			return;
		}
		super.unroll(bound, predecessor);
	}

	@Override
	public Jump getCopy() {
		return new Jump(this);
	}

	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public int compile(Arch target, int nextId, Event predecessor) {
		cId = nextId++;
		if(successor == null) {
			throw new RuntimeException("Malformed Jump event");
		}
		return successor.compile(target, nextId, this);
	}


	// Encoding
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void encodeCF(EncodeContext e, BoolExpr cond) {
		e.condition(label, e.cf(this));
		e.rule(e.eq(e.cf(this), e.condition(this)));
		encodeExec(e);
		successor.encodeCF(e, e.or());
	}
}
