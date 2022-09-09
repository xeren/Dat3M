package com.dat3m.dartagnan.wmm.relation.base.stat;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.EMPTY;

public class RelEmpty extends StaticRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitEmpty(this);
    }

    @Override
    public String getTerm() {
        return EMPTY;
    }
}
