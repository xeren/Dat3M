package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.wmm.relation.Relation;

/**
 *
 * @author Florian Furbach
 */
public class Acyclic extends Axiom {

    public Acyclic(Relation rel, boolean negated, boolean flag) {
        super(rel, negated, flag);
    }

    public Acyclic(Relation rel) {
        super(rel, false, false);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitAcyclicity(this);
    }

    @Override
    public String toString() {
        return (negated ? "~" : "") + "acyclic " + rel.getName();
    }
}