package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 *
 * @author Florian Furbach
 */
public class RelIntersection extends BinaryRelation {

    public static String makeTerm(Relation r1, Relation r2){
        return "(" + r1.getName() + "&" + r2.getName() + ")";
    }

    public RelIntersection(Relation r1, Relation r2) {
        super(r1, r2);
        term = makeTerm(r1, r2);
    }

    public RelIntersection(Relation r1, Relation r2, String name) {
        super(r1, r2, name);
        term = makeTerm(r1, r2);
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            maxTupleSet.addAll(r1.getMaxTupleSet());
            maxTupleSet.retainAll(r2.getMaxTupleSet());
        }
        return maxTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSetRecursive(){
        if(recursiveGroupId > 0 && maxTupleSet != null){
            maxTupleSet.addAll(r1.getMaxTupleSetRecursive());
            maxTupleSet.retainAll(r2.getMaxTupleSetRecursive());
            return maxTupleSet;
        }
        return getMaxTupleSet();
    }

    @Override
    public BoolExpr encodeApprox(EncodeContext context) {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : encodeTupleSet){
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), ctx.mkAnd(r1.edge(tuple), r2.edge(tuple))));
        }
        return enc;
    }

    @Override
    protected BoolExpr encodeIDL(EncodeContext context) {
        if(recursiveGroupId == 0){
            return encodeApprox(context);
        }

        BoolExpr enc = ctx.mkTrue();

        boolean recurseInR1 = (r1.getRecursiveGroupId() & recursiveGroupId) > 0;
        boolean recurseInR2 = (r2.getRecursiveGroupId() & recursiveGroupId) > 0;

        for(Tuple tuple : encodeTupleSet){
            BoolExpr opt1 = r1.edge(tuple);
            BoolExpr opt2 = r2.edge(tuple);
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), ctx.mkAnd(opt1, opt2)));

            if(recurseInR1){
                opt1 = ctx.mkAnd(opt1, ctx.mkGt(intCount(tuple), r1.intCount(tuple)));
            }
            if(recurseInR2){
                opt2 = ctx.mkAnd(opt2, ctx.mkGt(intCount(tuple), r2.intCount(tuple)));
            }
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), ctx.mkAnd(opt1, opt2)));
        }
        return enc;
    }

    @Override
    public BoolExpr encodeIteration(int groupId, int iteration){
        BoolExpr enc = ctx.mkTrue();

        if((groupId & recursiveGroupId) > 0 && iteration > lastEncodedIteration){
            lastEncodedIteration = iteration;

            if(iteration == 0 && isRecursive){
                for(Tuple tuple : encodeTupleSet){
                    enc = ctx.mkAnd(ctx.mkNot(edge(iteration, tuple)));
                }
            } else {
                int childIteration = isRecursive ? iteration - 1 : iteration;

                boolean recurseInR1 = (r1.getRecursiveGroupId() & groupId) > 0;
                boolean recurseInR2 = (r2.getRecursiveGroupId() & groupId) > 0;

                java.util.function.Function<?super Tuple,?extends BoolExpr> edge1
                        = recurseInR1 ? t->r1.edge(childIteration, t) : r1::edge;
                java.util.function.Function<?super Tuple,?extends BoolExpr> edge2
                        = recurseInR2 ? t->r2.edge(childIteration, t) : r2::edge;

                for(Tuple tuple : encodeTupleSet){
                    enc = ctx.mkAnd(enc, ctx.mkEq(edge(iteration, tuple), ctx.mkAnd(edge1.apply(tuple), edge2.apply(tuple))));
                }

                if(recurseInR1){
                    enc = ctx.mkAnd(enc, r1.encodeIteration(groupId, childIteration));
                }

                if(recurseInR2){
                    enc = ctx.mkAnd(enc, r2.encodeIteration(groupId, childIteration));
                }
            }
        }

        return enc;
    }

    @Override
    protected BoolExpr encodeFirstOrder(EncodeContext context) {
        return forall(0, (a,b)->ctx.mkEq(edge(a, b), ctx.mkAnd(r1.edge(a, b), r2.edge(a, b))),
                (a,b)->ctx.mkPattern(edge(a, b)),
                (a,b)->ctx.mkPattern(r1.edge(a, b), r2.edge(a, b)));
    }
}
