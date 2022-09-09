package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.relation.Relation;

public class RelRangeIdentity extends UnaryRelation {

    public RelRangeIdentity(Relation r1){
        super(r1);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitRangeIdentity(this, r1);
    }

    @Override
    public String getTerm() {
        return "[range(" + r1.getName() + ")]";
    }
}