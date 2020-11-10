package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.wmm.Computation;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.ArrayList;

public class Empty extends Axiom {

    public Empty(Relation rel) {
        super(rel);
    }

    public Empty(Relation rel, boolean negate) {
        super(rel, negate);
    }

    @Override
    public TupleSet getEncodeTupleSet(){
        return rel.getMaxTupleSet();
    }

    @Override
    protected BoolExpr _consistent(Context ctx) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            enc = ctx.mkAnd(enc, ctx.mkNot(Utils.edge(rel.getName(), tuple.getFirst(), tuple.getSecond(), ctx)));
        }
        return enc;
    }

    @Override
    protected BoolExpr _inconsistent(Context ctx) {
        BoolExpr enc = ctx.mkFalse();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            enc = ctx.mkOr(enc, Utils.edge(rel.getName(), tuple.getFirst(), tuple.getSecond(), ctx));
        }
        return enc;
    }

    @Override
    public BoolExpr encode(Context c, Computation r) {
        ArrayList<BoolExpr> enc = new ArrayList<>();
        rel.register(r).addParent((x,y)->rel.encode(c, r, enc, x, y));
        return c.mkAnd(enc.toArray(new BoolExpr[0]));
    }

    @Override
    protected BoolExpr _consistent(Context c, Computation r) {
        ArrayList<BoolExpr> enc = new ArrayList<>();
        rel.register(r).addParent((x,y)->enc.add(c.mkNot(rel.encode(c, r, null, x, y))));
        return c.mkAnd(enc.toArray(new BoolExpr[0]));
    }

    @Override
    protected String _toString() {
        return "empty " + rel.getName();
    }
}
