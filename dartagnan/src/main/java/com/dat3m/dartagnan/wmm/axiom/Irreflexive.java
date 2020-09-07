package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

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
    public TupleSet getEncodeTupleSet(ProgramCache p){
        TupleSet set = new TupleSet();
        for(Tuple tuple : rel.getMaxTupleSet(p)){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                set.add(tuple);
            }
        }
        return set;
    }

    @Override
    protected BoolExpr _consistent(EncodeContext e) {
        String name = rel.getName();
        return e.and(rel.getEncodeTupleSet().stream()
            .filter(t->t.getFirst().getCId() == t.getSecond().getCId())
            .map(t->e.not(e.edge(name, t))));
    }

    @Override
    protected BoolExpr _inconsistent(EncodeContext e) {
        String name = rel.getName();
        return e.or(rel.getEncodeTupleSet().stream()
            .filter(t->t.getFirst().getCId()==t.getSecond().getCId())
            .map(t->e.edge(name, t)));
    }

    @Override
    protected String _toString() {
        return "irreflexive " + rel.getName();
    }
}
