package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.arch.linux.utils.EType;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;

import java.util.LinkedList;

public class RelCrit extends StaticRelation {

	public RelCrit() {
		term = "crit";
	}

	@Override
	protected void update(EncodeContext e, TupleSet s) {
		for(Thread thread: e.program.getThreads())
			for(Event lock: thread.getCache().getEvents(FilterBasic.get(EType.RCU_LOCK)))
				for(Event unlock: thread.getCache().getEvents(FilterBasic.get(EType.RCU_UNLOCK)))
					if(lock.getCId() < unlock.getCId())
						s.add(new Tuple(lock, unlock));
	}

	// TODO: Not the most efficient implementation
	// Let's see if we need to keep a reference to a thread in events for anything else, and then optimize this method
	@Override
	protected void encodeApprox(EncodeContext e, Atom atom) {
		for(Thread thread: e.program.getThreads()) {
			for(Event lock: thread.getCache().getEvents(FilterBasic.get(EType.RCU_LOCK))) {
				for(Event unlock: thread.getCache().getEvents(FilterBasic.get(EType.RCU_UNLOCK))) {
					if(lock.getCId() < unlock.getCId()) {
						if(encodeTupleSet.contains(new Tuple(lock, unlock))) {
							LinkedList<BoolExpr> relation = new LinkedList<>();
							relation.add(lock.exec());
							relation.add(unlock.exec());
							for(Event otherLock: thread.getCache().getEvents(FilterBasic.get(EType.RCU_LOCK)))
								if(otherLock.getCId() > lock.getCId() && otherLock.getCId() < unlock.getCId())
									relation.add(e.not(atom.of(otherLock, unlock)));
							for(Event otherUnlock: thread.getCache().getEvents(FilterBasic.get(EType.RCU_UNLOCK)))
								if(otherUnlock.getCId() > lock.getCId() && otherUnlock.getCId() < unlock.getCId())
									relation.add(e.not(atom.of(lock, otherUnlock)));
							e.rule(e.eq(atom.of(lock, unlock), e.and(relation)));
						}
					}
				}
			}
		}
	}
}
