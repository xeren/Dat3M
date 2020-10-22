package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.utils.EncoderFO;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.Expr;

/**
 *
 * @author Florian Furbach
 */
public class RelUnion extends BinaryRelation {

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
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            maxTupleSet.addAll(r1.getMaxTupleSet());
            maxTupleSet.addAll(r2.getMaxTupleSet());
        }
        return maxTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSetRecursive(){
        if(recursiveGroupId > 0 && maxTupleSet != null){
            maxTupleSet.addAll(r1.getMaxTupleSetRecursive());
            maxTupleSet.addAll(r2.getMaxTupleSetRecursive());
            return maxTupleSet;
        }
        return getMaxTupleSet();
    }

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            BoolExpr opt1 = ctx.edge(r1.getName(), e1, e2);
            BoolExpr opt2 = ctx.edge(r2.getName(), e1, e2);
            if (Relation.PostFixApprox) {
                enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.mkOr(opt1, opt2), ctx.edge(getName(), e1, e2)));
            } else {
                enc = ctx.mkAnd(enc, ctx.mkEq(ctx.edge(getName(), e1, e2), ctx.mkOr(opt1, opt2)));
            }
        }
        return enc;
    }

    @Override
    protected BoolExpr encodeIDL() {
        if(recursiveGroupId == 0){
            return encodeApprox();
        }

        BoolExpr enc = ctx.mkTrue();

        boolean recurseInR1 = (r1.getRecursiveGroupId() & recursiveGroupId) > 0;
        boolean recurseInR2 = (r2.getRecursiveGroupId() & recursiveGroupId) > 0;

        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            BoolExpr opt1 = ctx.edge(r1.getName(), e1, e2);
            BoolExpr opt2 = ctx.edge(r2.getName(), e1, e2);
            enc = ctx.mkAnd(enc, ctx.mkEq(ctx.edge(this.getName(), e1, e2), ctx.mkOr(opt1, opt2)));

            if(recurseInR1){
                opt1 = ctx.mkAnd(opt1, ctx.mkGt(ctx.intCount(getName(), e1, e2), ctx.intCount(r1.getName(), e1, e2)));
            }
            if(recurseInR2){
                opt2 = ctx.mkAnd(opt2, ctx.mkGt(ctx.intCount(getName(), e1, e2), ctx.intCount(r2.getName(), e1, e2)));
            }
            enc = ctx.mkAnd(enc, ctx.mkEq(ctx.edge(getName(), e1, e2), ctx.mkOr(opt1, opt2)));
        }
        return enc;
    }

    @Override
    public BoolExpr encodeIteration(int groupId, int iteration){
        BoolExpr enc = ctx.mkTrue();

        if((groupId & recursiveGroupId) > 0 && iteration > lastEncodedIteration){
            lastEncodedIteration = iteration;

            String name = this.getName() + "_" + iteration;

            if(iteration == 0 && isRecursive){
                for(Tuple tuple : encodeTupleSet){
                    enc = ctx.mkAnd(ctx.mkNot(ctx.edge(name, tuple.getFirst(), tuple.getSecond())));
                }
            } else {
                int childIteration = isRecursive ? iteration - 1 : iteration;

                boolean recurseInR1 = (r1.getRecursiveGroupId() & groupId) > 0;
                boolean recurseInR2 = (r2.getRecursiveGroupId() & groupId) > 0;

                String r1Name = recurseInR1 ? r1.getName() + "_" + childIteration : r1.getName();
                String r2Name = recurseInR2 ? r2.getName() + "_" + childIteration : r2.getName();

                for(Tuple tuple : encodeTupleSet){
                    BoolExpr edge = ctx.edge(name, tuple.getFirst(), tuple.getSecond());
                    BoolExpr opt1 = ctx.edge(r1Name, tuple.getFirst(), tuple.getSecond());
                    BoolExpr opt2 = ctx.edge(r2Name, tuple.getFirst(), tuple.getSecond());
                    enc = ctx.mkAnd(enc, ctx.mkEq(edge, ctx.mkOr(opt1, opt2)));
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

    protected BoolExpr encodeFO() {
        EncoderFO c = (EncoderFO)ctx;
        Expr[] e = new Expr[]{c.bind(0), c.bind(1)};
        BoolExpr e1 = c.edge(r1.getName()).of(e[0], e[1]);
        BoolExpr e2 = c.edge(r2.getName()).of(e[0], e[1]);
        return c.forall(e, c.mkEq(c.edge(getName()).of(e[0], e[1]), c.mkOr(e1, e2)), c.pattern(e1), c.pattern(e2));
    }
}
