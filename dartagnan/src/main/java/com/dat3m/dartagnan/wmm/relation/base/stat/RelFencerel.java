package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.filter.FilterBasic;

public class RelFencerel extends StaticRelation {

    private final String fenceName;

    public RelFencerel(String fenceName) {
        this.fenceName = fenceName;
    }

    public String getFenceName() { return fenceName; }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitFences(this, FilterBasic.get(fenceName));
    }

    @Override
    public String getTerm() {
        return "fencerel(" + fenceName + ")";
    }
}
