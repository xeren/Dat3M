package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.wmm.Computation;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.Model;

public class Init extends MemEvent {

	private final IConst value;
	
	public Init(Address address, IConst value) {
		super(address, null);
		this.value = value;
		addFilters(EType.ANY, EType.VISIBLE, EType.MEMORY, EType.WRITE, EType.INIT);
	}

	private Init(Init other){
		super(other);
		this.value = other.value;
	}

	public IConst getValue(){
		return value;
	}

	@Override
	public void initialise(Context ctx) {
		super.initialise(ctx);
		memValueExpr = value.toZ3Int(ctx);
	}

	@Override
	public String toString() {
		return "*" + address + " := " + value;
	}

	@Override
	public String label(){
		return "W";
	}

	@Override
	public IConst getMemValue(){
		return value;
	}


	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Init getCopy(){
		return new Init(this);
	}

	@Override
	public void extract(Model m, Computation.Thread t) {
		t.init(cId, interpret(m, memAddressExpr), value.getValue());
	}
}