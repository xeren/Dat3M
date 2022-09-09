package com.dat3m.dartagnan.wmm.relation.base.local;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.IDD;

public class RelIdd extends BasicRegRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitInternalDataDependency(this);
    }

    @Override
    public String getTerm() {
        return IDD;
    }
}
