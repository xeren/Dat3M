package com.dat3m.dartagnan.wmm.relation.base.local;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.ADDRDIRECT;

public class RelAddrDirect extends BasicRegRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitAddressDependency(this);
    }

    @Override
    public String getTerm() {
        return ADDRDIRECT;
    }
}
