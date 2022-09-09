package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.relation.Relation;

/**
 *
 * @author Florian Furbach
 */
public class RelTrans extends UnaryRelation {

    public RelTrans(Relation r1) {
        super(r1);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitTransitiveClosure(this, r1);
    }

    public String getTerm() {
        return r1.getName() + "^+";
    }
}