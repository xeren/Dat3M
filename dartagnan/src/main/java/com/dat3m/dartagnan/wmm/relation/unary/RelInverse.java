package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.relation.Relation;

/**
 *
 * @author Florian Furbach
 */
public class RelInverse extends UnaryRelation {
    //TODO/Note: We can forward getSMTVar calls
    // to avoid encoding this completely!

    public RelInverse(Relation r1){
        super(r1);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitInverse(this, r1);
    }

    @Override
    public String getTerm() {
        return r1.getName() + "^-1";
    }
}