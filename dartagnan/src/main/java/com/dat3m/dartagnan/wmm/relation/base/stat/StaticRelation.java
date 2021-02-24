package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;

public abstract class StaticRelation extends Relation {

    public StaticRelation() {
        super();
    }

    public StaticRelation(String name) {
        super(name);
    }

    @Override
    public TupleSet getMinTupleSet() {
        return minTupleSet = getMaxTupleSet();
    }

    @Override
    protected BoolExpr encodeApprox() {
        assert encodeTupleSet.isEmpty();
        return ctx.mkTrue();
    }
}
