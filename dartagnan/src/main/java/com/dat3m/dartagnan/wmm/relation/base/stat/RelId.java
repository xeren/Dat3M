package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.filter.FilterBasic;

import static com.dat3m.dartagnan.program.event.Tag.VISIBLE;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.ID;

public class RelId extends StaticRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitIdentity(this, FilterBasic.get(VISIBLE));
    }

    @Override
    public String getTerm() {
        return ID;
    }
}
