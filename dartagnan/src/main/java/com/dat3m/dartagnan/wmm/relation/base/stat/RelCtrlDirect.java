package com.dat3m.dartagnan.wmm.relation.base.stat;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.CTRLDIRECT;

public class RelCtrlDirect extends StaticRelation {

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitControl(this);
    }

    @Override
    public String getTerm() {
        return CTRLDIRECT;
    }
}
