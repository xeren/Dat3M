package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.wmm.relation.Relation;

/**
 *
 * @author Florian Furbach
 */
public class RelIntersection extends BinaryRelation {

    public RelIntersection(Relation r1, Relation r2) {
        super(r1, r2);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitIntersection(this, r1, r2);
    }

    @Override
    public String getTerm() {
        return "(" + r1.getName() + "&" + r2.getName() + ")";
    }
}