package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.LinkedList;

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
    public BoolExpr encodeApprox(EncodeContext e) {
        return e.and(encodeTupleSet.stream()
            .map(tuple->e.eq(e.edge(this, tuple), e.and(e.edge(r1, tuple), e.edge(r2, tuple)))));
    }

    @Override
    protected BoolExpr encodeIDL(EncodeContext e) {
        if(recursiveGroupId == 0)
            return encodeApprox(e);

        LinkedList<BoolExpr> enc = new LinkedList<>();
        boolean recurseInR1 = (r1.getRecursiveGroupId() & recursiveGroupId) > 0;
        boolean recurseInR2 = (r2.getRecursiveGroupId() & recursiveGroupId) > 0;

        for(Tuple tuple : encodeTupleSet){
            BoolExpr opt1 = e.edge(r1, tuple);
            BoolExpr opt2 = e.edge(r2, tuple);
            enc.add(e.eq(e.edge(this, tuple), e.and(opt1, opt2)));

            if(recurseInR1)
                opt1 = e.and(opt1, e.lt(e.intCount(r1, tuple), e.intCount(this, tuple)));
            if(recurseInR2)
                opt2 = e.and(opt2, e.lt(e.intCount(r2, tuple), e.intCount(this, tuple)));
            enc.add(e.eq(e.edge(this, tuple), e.and(opt1, opt2)));
        }
        return e.and(enc);
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
    protected BoolExpr encodeFirstOrder(EncodeContext e) {
        EncodeContext.RelationPredicate edge = e.of(this);
        EncodeContext.RelationPredicate edge1 = e.of(r1);
        EncodeContext.RelationPredicate edge2 = e.of(r2);
        return e.forall(0, (a,b)->e.eq(edge.of(a, b), e.and(edge1.of(a, b), edge2.of(a, b))),
                (a,b)->e.pattern(edge.of(a, b)),
                (a,b)->e.pattern(edge1.of(a, b), edge2.of(a, b)));
    }
}
