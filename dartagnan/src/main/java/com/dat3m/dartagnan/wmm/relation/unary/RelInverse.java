package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.Computation;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.Context;

import java.util.HashSet;
import java.util.List;
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
            BoolExpr opt = Utils.edge(r1.getName(), e2, e1, ctx);
            enc = ctx.mkAnd(enc, ctx.mkEq(Utils.edge(this.getName(), e1, e2, ctx), opt));
        }
        return enc;
    }

    @Override
    public Computation.Relation register(Computation computation) {
        if(computation.relation.containsKey(this))
            return computation.relation.get(this);
        Computation.Relation c1 = r1.register(computation);
        Computation.Relation r = new Computation.Relation();
        computation.relation.put(this, r);
        c1.addParent((x,y)->r.addMax(y,x));
        return r;
    }

    @Override
    public BoolExpr encode(Context c, Computation r, List<BoolExpr> o, com.dat3m.dartagnan.wmm.Event x, com.dat3m.dartagnan.wmm.Event y) {
        return r1.encode(c, r, o, y, x);
    }
}
    

