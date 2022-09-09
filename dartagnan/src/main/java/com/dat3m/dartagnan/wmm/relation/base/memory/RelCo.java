package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.wmm.relation.Relation;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.CO;

public class RelCo extends Relation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitMemoryOrder(this);
    }

    @Override
    public String getTerm() {
        return CO;
    }
}