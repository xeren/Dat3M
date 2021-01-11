package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.CondJump;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Label;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.HashMap;
import java.util.HashSet;

/**
Relates branch events to all events whose control flow depends on the evaluation of the branch condition.
*/
public class RelCtrlDirect extends StaticRelation {

	public RelCtrlDirect() {
		term = "ctrlDirect";
	}

	@Override
	public TupleSet getMaxTupleSet() {
		if(maxTupleSet == null) {
			maxTupleSet = new TupleSet();
			for(Thread thread : program.getThreads()) {
				HashSet<CondJump> current = new HashSet<>();
				HashMap<Label,HashSet<CondJump>> message = new HashMap<>();
				for(Event e : thread.getCache().getEvents(Event.class)) {
					if(e instanceof Label) {
						HashSet<CondJump> m = message.remove(e);
						current.addAll(m);
						HashSet<CondJump> pending = new HashSet<>();
						for(HashSet<CondJump> p: message.values())
							pending.addAll(p);
						current.retainAll(pending);
					}
					for(CondJump j: current) {
						maxTupleSet.add(new Tuple(j, e));
					}
					if(e instanceof CondJump) {
						Label l = ((CondJump) e).getLabel();
						message.computeIfAbsent(l, k->new HashSet<>()).addAll(current);
						if(((CondJump) e).isUnconditional())
							current.clear();
						else
							current.add((CondJump) e);
					}
				}
			}
		}
		return maxTupleSet;
	}
}
