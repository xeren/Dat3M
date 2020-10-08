package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;

public class RelCartesian extends StaticRelation {
	private final FilterAbstract filter1;
	private final FilterAbstract filter2;

	public static String makeTerm(FilterAbstract filter1, FilterAbstract filter2) {
		return "(" + filter1 + "*" + filter2 + ")";
	}

	public RelCartesian(FilterAbstract filter1, FilterAbstract filter2) {
		this.filter1 = filter1;
		this.filter2 = filter2;
		this.term = makeTerm(filter1, filter2);
	}

	public RelCartesian(FilterAbstract filter1, FilterAbstract filter2, String name) {
		super(name);
		this.filter1 = filter1;
		this.filter2 = filter2;
		this.term = makeTerm(filter1, filter2);
	}

	@Override
	public void update(ProgramCache p, TupleSet s) {
		List<Event> l1 = p.cache(filter1);
		List<Event> l2 = p.cache(filter2);
		for(Event e1: l1)
			for(Event e2: l2)
				s.add(new Tuple(e1, e2));
	}
}
