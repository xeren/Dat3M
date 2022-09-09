package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.relation.Relation;

/**
 *
 * @author Florian Furbach
 */
public class RelTransRef extends RelTrans {

    public RelTransRef(Relation r1) {
        super(r1);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitReflexiveTransitiveClosure(this, r1);
    }

    public String getTerm() {
        return r1.getName() + "^*";
    }
}