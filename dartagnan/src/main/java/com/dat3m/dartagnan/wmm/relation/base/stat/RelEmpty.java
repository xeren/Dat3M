package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelEmpty extends Relation {

    public RelEmpty(String name) {
        super(name);
        term = name;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
        }
        return maxTupleSet;
    }

    @Override
    protected BoolExpr encodeApprox(EncodeContext context) {
        return ctx.mkTrue();
    }

    @Override
    protected BoolExpr encodeFirstOrder(EncodeContext context) {
        return forall(0, (a,b)->ctx.mkNot(edge(a,b)),
                (a,b)->ctx.mkPattern(edge(a,b)));
    }
}
