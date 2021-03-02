package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.wmm.utils.Tuple;

import java.util.Collection;

public class RelLoc extends Relation {

    public RelLoc(){
        term = "loc";
    }

	@Override
	protected void mkMaxTupleSet(){
            Collection<Event> events = program.getCache().getEvents(FilterBasic.get(EType.MEMORY));
            for(Event e1 : events){
			MemEvent m1 = (MemEvent)e1;
                for(Event e2 : events){
				if(e1.getCId() == e2.getCId())
					continue;
				MemEvent m2 = (MemEvent)e2;
				if(MemEvent.canAddressTheSameLocation(m1,m2))
					addMaxTuple(e1,e2,MemEvent.mustAddressTheSameLocation(m1,m2));
                }
            }
	}

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : encodeTupleSet) {
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), ctx.mkAnd(
                    program.executesBoth(ctx,tuple.getFirst(),tuple.getSecond()),
                    ctx.mkEq(((MemEvent)tuple.getFirst()).getMemAddressExpr(), ((MemEvent)tuple.getSecond()).getMemAddressExpr())
            )));
        }
        return enc;
    }
}
