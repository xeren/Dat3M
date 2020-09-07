package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class Empty extends Axiom {

    public Empty(Relation rel) {
        super(rel);
    }

    public Empty(Relation rel, boolean negate) {
        super(rel, negate);
    }

    @Override
    public TupleSet getEncodeTupleSet(ProgramCache p){
        return rel.getMaxTupleSet(p);
    }

    @Override
    protected BoolExpr _consistent(EncodeContext e) {
        String name = rel.getName();
        return e.and(rel.getEncodeTupleSet().stream().map(t->e.not(e.edge(name, t))));
    }

    @Override
    protected BoolExpr _inconsistent(EncodeContext e) {
        String name = rel.getName();
        return e.or(rel.getEncodeTupleSet().stream().map(t->e.edge(name, t)));
    }

    @Override
    protected String _toString() {
        return "empty " + rel.getName();
    }
}
