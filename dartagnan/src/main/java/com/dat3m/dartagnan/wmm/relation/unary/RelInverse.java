package com.dat3m.dartagnan.wmm.relation.unary;

import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Florian Furbach
 */
public class RelInverse extends UnaryRelation {

    public static String makeTerm(Relation r1){
        return r1.getName() + "^-1";
    }

    public RelInverse(Relation r1){
        super(r1);
        term = makeTerm(r1);
    }

    public RelInverse(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Tuple pair : r1.getMaxTupleSet()){
                maxTupleSet.add(new Tuple(pair.getSecond(), pair.getFirst()));
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
            TupleSet invSet = new TupleSet();
            for(Tuple pair : activeSet){
                invSet.add(new Tuple(pair.getSecond(), pair.getFirst()));
            }
            r1.addEncodeTupleSet(invSet);
        }
    }

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1, e2), r1.edge(e2, e1)));
        }
        return enc;
    }

    @Override
    protected BoolExpr encodeFirstOrder() {
        return forall(0, (a,b)->ctx.mkEq(edge(a, b), r1.edge(b, a)),
                (a,b)->ctx.mkPattern(edge(a, b)),
                (a,b)->ctx.mkPattern(r1.edge(b, a)));
    }
}
    

