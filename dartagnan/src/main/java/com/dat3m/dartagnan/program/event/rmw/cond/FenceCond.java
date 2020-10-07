package com.dat3m.dartagnan.program.event.rmw.cond;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Fence;

public class FenceCond extends Fence {

    private final RMWReadCond loadEvent;

    public FenceCond (RMWReadCond loadEvent, String name){
        super(name);
        this.loadEvent = loadEvent;
    }

    @Override
    public String toString(){
        return String.format("%1$-" + Event.PRINT_PAD_EXTRA + "s", super.toString()) + loadEvent.condToString();
    }

    @Override
    protected void encodeExec(EncodeContext e){
        e.rule(e.eq(e.exec(this), e.and(e.cf(this), loadEvent.getCond(e))));
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void unroll(int bound, Event predecessor) {
        throw new RuntimeException("FenceCond cannot be unrolled: event must be generated during compilation");
    }
}
