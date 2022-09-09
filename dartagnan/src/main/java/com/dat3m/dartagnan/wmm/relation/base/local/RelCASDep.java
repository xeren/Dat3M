package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.CASDEP;

public class RelCASDep extends StaticRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitCompareAndSwapDependency(this);
    }

    @Override
    public String getTerm() {
        return CASDEP;
    }
}