package com.dat3m.dartagnan.wmm.relation.base.stat;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.EXT;

public class RelExt extends StaticRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitExternal(this);
    }

    @Override
    public String getTerm() {
        return EXT;
    }
}
