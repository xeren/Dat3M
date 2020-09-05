package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 *
 * @author Florian Furbach
 */
public class RelMinus extends BinaryRelation {

    public static String makeTerm(Relation r1, Relation r2){
        return "(" + r1.getName() + "\\" + r2.getName() + ")";
    }

    public RelMinus(Relation r1, Relation r2) {
        super(r1, r2);
        term = makeTerm(r1, r2);
    }

    public RelMinus(Relation r1, Relation r2, String name) {
        super(r1, r2, name);
        term = makeTerm(r1, r2);
    }

    @Override
    public void initialise(Program program, Context ctx, Settings settings){
        super.initialise(program, ctx, settings);
        if(r2.getRecursiveGroupId() > 0){
            throw new RuntimeException("Relation " + r2.getName() + " cannot be recursive since it occurs in a set minus.");
        }
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            maxTupleSet.addAll(r1.getMaxTupleSet());
            r2.getMaxTupleSet();
        }
        return maxTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSetRecursive(){
        if(recursiveGroupId > 0 && maxTupleSet != null){
            maxTupleSet.addAll(r1.getMaxTupleSetRecursive());
            return maxTupleSet;
        }
        return getMaxTupleSet();
    }

    @Override
    protected BoolExpr encodeApprox(EncodeContext e) {
        return e.and(encodeTupleSet.stream().map(Relation.PostFixApprox
            ? tuple->e.or(e.edge(this, tuple), e.not(e.edge(r1, tuple)), e.edge(r2, tuple))
            : tuple->e.eq(e.edge(this, tuple), e.and(e.edge(r1, tuple), e.not(e.edge(r2, tuple))))));
    }

    @Override
    protected BoolExpr encodeIDL(EncodeContext e) {
        if(recursiveGroupId == 0)
            return encodeApprox(e);

        return e.and(encodeTupleSet.stream().map(tuple->e.eq(e.edge(this, tuple), e.and(
            e.edge(r1, tuple),
            e.not(e.edge(r2, tuple)),
            e.lt(e.intCount(r1, tuple), e.intCount(this, tuple))))));
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
                boolean recurse = (r1.getRecursiveGroupId() & groupId) > 0;
                assert (r2.getRecursiveGroupId() & groupId) == 0;

                java.util.function.Function<?super Tuple,?extends BoolExpr> e
                        = recurse ? t->r1.edge(childIteration, t) : r1::edge;

                for(Tuple tuple : encodeTupleSet){
                    enc = ctx.mkAnd(enc, ctx.mkEq(edge(iteration, tuple), ctx.mkAnd(e.apply(tuple), ctx.mkNot(r2.edge(tuple)))));
                }

                if(recurse){
                    enc = ctx.mkAnd(enc, r1.encodeIteration(groupId, childIteration));
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
        return e.forall(0, (a,b)->e.eq(edge.of(a, b), e.and(edge1.of(a, b), e.not(edge2.of(a, b)))),
                (a,b)->e.pattern(edge.of(a, b)),
                (a,b)->e.pattern(edge1.of(a, b), edge2.of(a, b)));
    }
}
