package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.wmm.Computation;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.ArrayList;

/**
 *
 * @author Florian Furbach
 */
public class Irreflexive extends Axiom {

    public Irreflexive(Relation rel) {
        super(rel);
    }

    public Irreflexive(Relation rel, boolean negate) {
        super(rel, negate);
    }

    @Override
    public TupleSet getEncodeTupleSet(){
        TupleSet set = new TupleSet();
        for(Tuple tuple : rel.getMaxTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                set.add(tuple);
            }
        }
        return set;
    }

    @Override
    protected BoolExpr _consistent(Context ctx) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                enc = ctx.mkAnd(enc, ctx.mkNot(Utils.edge(rel.getName(), tuple.getFirst(), tuple.getFirst(), ctx)));
            }
        }
        return enc;
    }

    @Override
    protected BoolExpr _inconsistent(Context ctx) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                enc = ctx.mkOr(enc, Utils.edge(rel.getName(), tuple.getFirst(), tuple.getFirst(), ctx));
            }
        }
        return enc;
    }

    @Override
    public BoolExpr encode(Context c, Computation r) {
        ArrayList<BoolExpr> enc = new ArrayList<>();
        rel.register(r).addParent((x,y)->{if(x==y)rel.encode(c,r,enc,x,y);});
        return c.mkAnd(enc.toArray(new BoolExpr[0]));
    }

    @Override
    protected BoolExpr _consistent(Context c, Computation r) {
        ArrayList<BoolExpr> enc = new ArrayList<>();
        rel.register(r).addParent((x,y)->{if(x==y)enc.add(c.mkNot(rel.encode(c,r,null,x,y)));});
        return c.mkAnd(enc.toArray(new BoolExpr[0]));
    }

    @Override
    protected String _toString() {
        return "irreflexive " + rel.getName();
    }
}
