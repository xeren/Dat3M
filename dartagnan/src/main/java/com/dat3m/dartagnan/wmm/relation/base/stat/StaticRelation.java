package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.Context;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;

public abstract class StaticRelation extends Relation {

    public StaticRelation() {
        super();
    }

    public StaticRelation(String name) {
        super(name);
    }

    @Override
    public TupleSet getMinTupleSet(){
        if(minTupleSet == null){
            minTupleSet = getMaxTupleSet();
        }
        return minTupleSet;
    }

    @Override
    protected BoolExpr encodeApprox(Context ctx) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : encodeTupleSet) {
            BoolExpr rel = this.getSMTVar(tuple, ctx);
            enc = ctx.mkAnd(enc, ctx.mkEq(rel, ctx.mkAnd(getExecPair(tuple, ctx))));
        }
        return enc;
    }
}
