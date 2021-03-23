package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;

public class RelId extends StaticRelation {

    public RelId(){
        term = "id";
    }

	@Override
	protected void mkMaxTupleSet(){
		for(Event e : program.getCache().getEvents(FilterBasic.get(EType.VISIBLE)))
			addMaxTuple(e,e,true);
	}
}
