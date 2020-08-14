package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;

public abstract class StaticRelation extends Relation {

    public StaticRelation() {
        super();
    }

    public StaticRelation(String name) {
        super(name);
    }

    @FunctionalInterface
    protected interface Atom {
        BoolExpr of(Event first, Event second);
        default BoolExpr of(Tuple tuple) {
            return of(tuple.getFirst(), tuple.getSecond());
        }
    }

    protected BoolExpr encodeApprox(Atom atom) {
        return and(encodeTupleSet.stream().map(tuple->ctx.mkEq(atom.of(tuple), ctx.mkAnd(tuple.getFirst().exec(), tuple.getSecond().exec()))));
    }

    @Override
    protected BoolExpr encodeApprox() {
        return encodeApprox(this::edge);
    }

    @Override
    protected BoolExpr encodeFirstOrder() {
        return encodeApprox((a,b)->edge(ctx.mkNumeral(a.getCId(), eventSort), ctx.mkNumeral(b.getCId(), eventSort)));
    }
}
