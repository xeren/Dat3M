package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;
import java.util.ListIterator;

public class RelExt extends StaticRelation {

	public RelExt() {
		term = "ext";
	}

	@Override
	protected void update(EncodeContext e, TupleSet s) {
		List<Thread> threads = e.program.getThreads();
		ListIterator<Thread> it1 = threads.listIterator();
		while(it1.hasNext()) {
			Thread t1 = it1.next();
			ListIterator<Thread> it2 = threads.listIterator(it1.nextIndex());
			while(it2.hasNext()) {
				Thread t2 = it2.next();
				for(Event e1: t1.getCache().getEvents(FilterBasic.get(EType.VISIBLE))) {
					for(Event e2: t2.getCache().getEvents(FilterBasic.get(EType.VISIBLE))) {
						s.add(new Tuple(e1, e2));
						s.add(new Tuple(e2, e1));
					}
				}
			}
		}
	}
}
