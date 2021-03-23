package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;

public class RelSetIdentity extends StaticRelation {

    protected FilterAbstract filter;

    public static String makeTerm(FilterAbstract filter){
        return "[" + filter + "]";
    }

    public RelSetIdentity(FilterAbstract filter) {
        this.filter = filter;
        term = makeTerm(filter);
    }

    public RelSetIdentity(FilterAbstract filter, String name) {
        super(name);
        this.filter = filter;
        term = makeTerm(filter);
    }

	@Override
	protected void mkMaxTupleSet(){
		for(Event e : program.getCache().getEvents(filter))
			addMaxTuple(e,e);
	}
}
