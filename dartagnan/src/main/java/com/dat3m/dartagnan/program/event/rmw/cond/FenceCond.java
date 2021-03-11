package com.dat3m.dartagnan.program.event.rmw.cond;

import com.dat3m.dartagnan.program.ControlBlock;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Fence;

public class FenceCond extends Fence {

    private final RMWReadCond loadEvent;
	private BoolExpr execVar;

    public FenceCond (RMWReadCond loadEvent, String name){
        super(name);
        this.loadEvent = loadEvent;
    }

    @Override
    public String toString(){
        return String.format("%1$-" + Event.PRINT_PAD_EXTRA + "s", super.toString()) + loadEvent.condToString();
    }

	@Override
	public ControlBlock initialise(Context c, ControlBlock b, ControlMessage m) {
		ControlBlock r = super.initialise(c,b,m);
		execVar = c.mkBoolConst("exec(" + repr() + ")");
		return r;
	}

	@Override
	public BoolExpr exec() {
		return execVar;
	}

	@Override
	public void encode(Context c, Constraint o){
		super.encode(c,o);
		o.add(c.mkEq(execVar,c.mkAnd(control.variable,loadEvent.getCond())));
	}

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void unroll(int bound, Event predecessor) {
        throw new RuntimeException("FenceCond cannot be unrolled: event must be generated during compilation");
    }
}
