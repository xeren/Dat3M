package com.dat3m.dartagnan.wmm.axiom;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;

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
        String name = rel.getName();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            enc = ctx.mkAnd(enc, ctx.mkNot(edge(name, tuple.getFirst(), tuple.getSecond(), ctx)));
        }
        return enc;
    }

    @Override
    protected BoolExpr _inconsistent(Context ctx) {
        BoolExpr enc = ctx.mkFalse();
        String name = rel.getName();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            enc = ctx.mkOr(enc, edge(name, tuple.getFirst(), tuple.getSecond(), ctx));
        }
        return enc;
    }

    @Override
    protected String _toString() {
        return "empty " + rel.getName();
    }
}
