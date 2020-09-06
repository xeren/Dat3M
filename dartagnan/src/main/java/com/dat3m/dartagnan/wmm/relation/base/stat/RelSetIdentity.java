package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelSetIdentity extends StaticRelation {

	protected FilterAbstract filter;

	public static String makeTerm(FilterAbstract filter) {
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
	public void update(EncodeContext e, TupleSet s) {
		for(Event x: e.cache(filter))
			s.add(new Tuple(x, x));
	}
}
