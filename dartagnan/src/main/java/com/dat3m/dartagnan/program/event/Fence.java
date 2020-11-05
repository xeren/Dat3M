package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.Computation;
import com.microsoft.z3.Model;

public class Fence extends Event {

	protected final String name;

	public Fence(String name){
        this.name = name;
        this.addFilters(EType.ANY, EType.VISIBLE, EType.FENCE, name);
	}

	protected Fence(Fence other){
		super(other);
		this.name = other.name;
	}

	public String getName(){
		return name;
	}

	@Override
	public String toString() {
		return getName();
	}

	@Override
	public String label(){
		return getName();
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Fence getCopy(){
		return new Fence(this);
	}

	@Override
	public void extract(Model m, Computation.Thread t) {
		t.fence(name);
	}
}
