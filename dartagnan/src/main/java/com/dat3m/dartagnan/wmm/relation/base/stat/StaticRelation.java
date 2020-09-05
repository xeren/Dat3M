package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
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

    protected BoolExpr encodeApprox(EncodeContext context, Atom atom) {
        return context.and(encodeTupleSet.stream().map(tuple->ctx.mkEq(atom.of(tuple), ctx.mkAnd(tuple.getFirst().exec(), tuple.getSecond().exec()))));
    }

    @Override
    protected BoolExpr encodeApprox(EncodeContext context) {
        return encodeApprox(context, this::edge);
    }

    @Override
    protected BoolExpr encodeFirstOrder(EncodeContext e) {
        EncodeContext.RelationPredicate edge = e.of(this);
        return encodeApprox(e, (a,b)->edge.of(e.event(a), e.event(b)));
    }
}
