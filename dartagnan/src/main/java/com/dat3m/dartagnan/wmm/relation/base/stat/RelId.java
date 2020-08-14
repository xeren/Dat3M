package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;

public class RelId extends StaticRelation {

    public RelId(){
        term = "id";
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Event e : program.getCache().getEvents(FilterBasic.get(EType.VISIBLE))){
                maxTupleSet.add(new Tuple(e, e));
            }
        }
        return maxTupleSet;
    }

    @Override
    protected BoolExpr encodeFirstOrder() {
        return ctx.mkAnd(
            forall(0, (a,b)->ctx.mkImplies(edge(a, b), ctx.mkEq(a, b)), (a,b)->ctx.mkPattern(edge(a, b))),
            forall(0, a->edge(a, a), ctx::mkPattern));
    }
}
