package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelExt extends StaticRelation {

	public RelExt() {
		term = "ext";
	}

	@Override
	protected void update(ProgramCache p, TupleSet s) {
		ProgramCache.Thread[] threads = p.thread();
		for(int it1 = 0; it1 < threads.length; it1++) {
			ProgramCache.Thread t1 = threads[it1];
			for(int it2 = it1 + 1; it2 < threads.length; it2++) {
				ProgramCache.Thread t2 = threads[it2];
				for(Event e1: t1.cache(FilterBasic.get(EType.VISIBLE))) {
					for(Event e2: t2.cache(FilterBasic.get(EType.VISIBLE))) {
						s.add(new Tuple(e1, e2));
						s.add(new Tuple(e2, e1));
					}
				}
			}
		}
	}
}
