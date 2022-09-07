package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 *
 * @author Florian Furbach
 */
public class Irreflexive extends Axiom {

    public Irreflexive(Relation rel, boolean negated, boolean flag) {
        super(rel, negated, flag);
    }

    public Irreflexive(Relation rel) {
        super(rel, false, false);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitIrreflexivity(this);
    }

    @Override
    public TupleSet getEncodeTupleSet(){
        TupleSet set = new TupleSet();
        rel.getMaxTupleSet().stream().filter(Tuple::isLoop).forEach(set::add);
        return set;
    }

    @Override
    public String toString() {
        return (negated ? "~" : "") + "irreflexive " + rel.getName();
    }
}