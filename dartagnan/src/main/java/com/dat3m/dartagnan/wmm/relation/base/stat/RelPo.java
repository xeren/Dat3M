package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.program.filter.FilterBasic;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.PO;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.POWITHLOCALEVENTS;

public class RelPo extends StaticRelation {

    private final FilterAbstract filter;

    public RelPo(){
        this(false);
    }

    public RelPo(boolean includeLocalEvents){
        if(includeLocalEvents){
            filter = FilterBasic.get(Tag.ANY);
        } else {
            filter = FilterBasic.get(Tag.VISIBLE);
        }
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitProgramOrder(this);
    }

    @Override
    public String getTerm() {
        return FilterBasic.get(Tag.ANY).equals(filter) ? POWITHLOCALEVENTS : PO;
    }
}
