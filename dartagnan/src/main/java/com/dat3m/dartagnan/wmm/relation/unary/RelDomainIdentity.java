package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.relation.Relation;

public class RelDomainIdentity extends UnaryRelation {

    public RelDomainIdentity(Relation r1){
        super(r1);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitDomainIdentity(this, r1);
    }

    @Override
    public String getTerm() {
        return "[domain(" + r1.getName() + ")]";
    }
}
