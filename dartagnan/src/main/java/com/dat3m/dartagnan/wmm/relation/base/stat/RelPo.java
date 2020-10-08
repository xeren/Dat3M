package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.List;
import java.util.ListIterator;

public class RelPo extends StaticRelation {

	private final FilterAbstract filter;

	public RelPo() {
		this(false);
	}

	public RelPo(boolean includeLocalEvents) {
		if(includeLocalEvents) {
			term = "_po";
			filter = FilterBasic.get(EType.ANY);
		} else {
			term = "po";
			filter = FilterBasic.get(EType.VISIBLE);
		}
	}

	@Override
	public void update(ProgramCache p, TupleSet s) {
		for(ProgramCache.Thread t: p.thread()) {
			List<Event> events = t.cache(filter);
			ListIterator<Event> it1 = events.listIterator();
			while(it1.hasNext()) {
				Event e1 = it1.next();
				ListIterator<Event> it2 = events.listIterator(it1.nextIndex());
				while(it2.hasNext())
					s.add(new Tuple(e1, it2.next()));
			}
		}
	}
}
