package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;

import java.util.HashSet;
import java.util.Set;

public class RelRangeIdentity extends UnaryRelation {

    public static String makeTerm(Relation r1){
        return "[range(" + r1.getName() + ")]";
    }

    public RelRangeIdentity(Relation r1){
        super(r1);
        term = makeTerm(r1);
    }

    public RelRangeIdentity(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Tuple tuple : r1.getMaxTupleSet()){
                maxTupleSet.add(new Tuple(tuple.getSecond(), tuple.getSecond()));
            }
        }
        return maxTupleSet;
    }

    @Override
    public void addEncodeTupleSet(TupleSet tuples){
        encodeTupleSet.addAll(tuples);
        Set<Tuple> activeSet = new HashSet<>(tuples);
        activeSet.retainAll(maxTupleSet);
        if(!activeSet.isEmpty()){
            TupleSet r1Set = new TupleSet();
            for(Tuple tuple : activeSet){
                r1Set.addAll(r1.getMaxTupleSet().getBySecond(tuple.getFirst()));
            }
            r1.addEncodeTupleSet(r1Set);
        }
    }

    @Override
    protected BoolExpr encodeApprox(EncodeContext context) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple1 : encodeTupleSet){
            Event e = tuple1.getFirst();
            BoolExpr opt = ctx.mkFalse();
            for(Tuple tuple2 : r1.getMaxTupleSet().getBySecond(e)){
                //FIXME do not discard
                opt = ctx.mkOr(r1.edge(tuple2.getFirst(), e));
            }
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e, e), opt));
        }
        return enc;
    }

    @Override
    protected BoolExpr encodeFirstOrder(EncodeContext e) {
        EncodeContext.RelationPredicate edge = e.of(this);
        EncodeContext.RelationPredicate edge1 = e.of(r1);
        return e.and(
            e.forall(0, (a,b)->e.eq(edge.of(a, b), e.and(
                    e.eq(a, b),
                    e.exists(2, c->edge1.of(c, b), c->e.pattern(edge1.of(c, b))))),
                (a,b)->e.pattern(edge.of(a, b))),
            e.forall(0, (a,b)->e.implies(edge1.of(a, b), edge.of(b, b)),
                (a,b)->e.pattern(edge1.of(a, b))));
    }
}
