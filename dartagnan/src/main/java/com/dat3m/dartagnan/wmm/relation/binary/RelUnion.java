package com.dat3m.dartagnan.wmm.relation.binary;

import com.google.common.collect.Sets;
import com.microsoft.z3.BoolExpr;

import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.Context;

/**
 *
 * @author Florian Furbach
 */
public class RelUnion extends BinaryRelation {
    //TODO: We can make use of minTupleSet when propagating active sets
    // If (e1,e2) is in min(r1), then we don't need to propagate it to r2
    // Actually, we might not need to propagate at all if it is in the unions minSet.
    // CARE 1: If it is in the minSet of both, we need to propagate to at least one of them
    // Care 2: When encoding, we need to make use of this fact

    public static String makeTerm(Relation r1, Relation r2){
        return "(" + r1.getName() + "+" + r2.getName() + ")";
    }

    public RelUnion(Relation r1, Relation r2) {
        super(r1, r2);
        term = makeTerm(r1, r2);
    }

    public RelUnion(Relation r1, Relation r2, String name) {
        super(r1, r2, name);
        term = makeTerm(r1, r2);
    }

    @Override
    public TupleSet getMinTupleSet(){
        if(minTupleSet == null){
            minTupleSet = new TupleSet(Sets.union(r1.getMinTupleSet(), r2.getMinTupleSet()));
        }
        return minTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet(Sets.union(r1.getMaxTupleSet(), r2.getMaxTupleSet()));
        }
        return maxTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSetRecursive(){
        if(recursiveGroupId > 0 && maxTupleSet != null){
            maxTupleSet.addAll(Sets.union(r1.getMaxTupleSetRecursive(), r2.getMaxTupleSetRecursive()));
            return maxTupleSet;
        }
        return getMaxTupleSet();
    }

    @Override
    protected BoolExpr encodeApprox(Context ctx) {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : encodeTupleSet){
            BoolExpr opt1 = r1.getSMTVar(tuple, ctx);
            BoolExpr opt2 = r2.getSMTVar(tuple, ctx);
            if (Relation.PostFixApprox) {
                enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.mkOr(opt1, opt2), this.getSMTVar(tuple, ctx)));
            } else {
                enc = ctx.mkAnd(enc, ctx.mkEq(this.getSMTVar(tuple, ctx), ctx.mkOr(opt1, opt2)));
            }
        }
        return enc;
    }
}