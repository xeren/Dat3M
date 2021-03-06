package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;

public class RelLoc extends Relation {

    public RelLoc(){
        term = "loc";
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            List<Event> events = program.getCache().getEvents(FilterBasic.get(EType.MEMORY));
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
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), ctx.mkAnd(
                    tuple.getFirst().exec(),
                    tuple.getSecond().exec(),
                    ctx.mkEq(((MemEvent)tuple.getFirst()).getMemAddressExpr(), ((MemEvent)tuple.getSecond()).getMemAddressExpr())
            )));
        }
        return enc;
    }

    @Override
    protected BoolExpr encodeFirstOrder() {
        //TODO restrict to M*M
        List<Event> events = program.getCache().getEvents(FilterBasic.get(EType.MEMORY));
        return ctx.mkAnd(and(events.stream().map(MemEvent.class::cast).flatMap(a->events.stream().map(MemEvent.class::cast)
                .filter(b->a.getCId() != b.getCId())
                .filter(b->MemEvent.canAddressTheSameLocation(a, b))
                .map(b->ctx.mkEq(
                    edge(ctx.mkNumeral(a.getCId(), eventSort), ctx.mkNumeral(b.getCId(), eventSort)),
                    ctx.mkAnd(a.exec(), b.exec(), ctx.mkEq(a.getMemAddressExpr(), b.getMemAddressExpr())))))));
    }
}
