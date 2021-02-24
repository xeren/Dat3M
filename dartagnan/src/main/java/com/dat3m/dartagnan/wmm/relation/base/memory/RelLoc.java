package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.Collection;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;

public class RelLoc extends Relation {

    public RelLoc(){
        term = "loc";
    }

    @Override
    public TupleSet getMinTupleSet() {
        if(null == minTupleSet) {
            minTupleSet = new TupleSet();
            for(Event x : program.getCache().getEvents(FilterBasic.get(EType.MEMORY)))
                for(Event y : program.location(x))
                    minTupleSet.add(new Tuple(x,y));
        }
        return minTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            Collection<Event> events = program.getCache().getEvents(FilterBasic.get(EType.MEMORY));
            for(Event e1 : events){
                for(Event e2 : events){
                    if(e1.getCId() != e2.getCId() && MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent)e2)){
                        maxTupleSet.add(new Tuple(e1, e2));
                    }
                }
            }
        }
        return maxTupleSet;
    }

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : encodeTupleSet) {
            BoolExpr rel = edge(this.getName(), tuple.getFirst(), tuple.getSecond(), ctx);
            enc = ctx.mkAnd(enc, ctx.mkEq(rel, ctx.mkAnd(
                    ctx.mkAnd(tuple.getFirst().exec(), tuple.getSecond().exec()),
                    ctx.mkEq(((MemEvent)tuple.getFirst()).getMemAddressExpr(), ((MemEvent)tuple.getSecond()).getMemAddressExpr())
            )));
        }
        return enc;
    }
}
