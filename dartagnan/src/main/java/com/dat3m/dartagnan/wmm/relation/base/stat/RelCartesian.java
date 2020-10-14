package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

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

	@Override
	public void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		super.encodeFirstOrder(e, p);
		filter1.encodeFO(e, p);
		filter2.encodeFO(e, p);
	}

	@Override
	public Stream<Clause> termFO(Counter t, int a, int b) {
		Clause[] c1 = filter1.nameFO(a).toArray(Clause[]::new);
		return filter2.nameFO(b).flatMap(c2->Arrays.stream(c1).map(c2::combine));
	}
}
