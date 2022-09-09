package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.filter.FilterAbstract;

//TODO: This relation may contain non-visible events. Is this reasonable?
public class RelSetIdentity extends StaticRelation {

    protected FilterAbstract filter;

    public RelSetIdentity(FilterAbstract filter) {
        this.filter = filter;
    }

    public FilterAbstract getFilter() {
        return filter;
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitIdentity(this, filter);
    }

    public String getTerm() {
        return "[" + filter + "]";
    }
}
