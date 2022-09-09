package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.CRIT;

public class RelCrit extends StaticRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitCriticalSections(this);
    }

    @Override
    public String getTerm() {
        return CRIT;
    }
}