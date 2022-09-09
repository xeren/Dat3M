package com.dat3m.dartagnan.wmm.relation.base.stat;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.INT;

public class RelInt extends StaticRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitInternal(this);
    }

    @Override
    public String getTerm() {
        return INT;
    }
}
