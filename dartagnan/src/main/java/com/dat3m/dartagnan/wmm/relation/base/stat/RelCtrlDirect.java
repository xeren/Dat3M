package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.If;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;

public class RelCtrlDirect extends StaticRelation {

	public RelCtrlDirect() {
		term = "ctrlDirect";
	}

	@Override
	protected void update(TupleSet s) {
		for(Thread thread: program.getThreads()) {
			for(Event e1: thread.getCache().getEvents(FilterBasic.get(EType.CMP))) {
				for(Event e2: ((If) e1).getMainBranchEvents())
					s.add(new Tuple(e1, e2));
				for(Event e2: ((If) e1).getElseBranchEvents())
					s.add(new Tuple(e1, e2));
			}

			List<Event> condJumps = thread.getCache().getEvents(FilterBasic.get(EType.COND_JUMP));
			if(!condJumps.isEmpty())
				for(Event e2: thread.getCache().getEvents(FilterBasic.get(EType.ANY)))
					for(Event e1: condJumps)
						if(e1.getCId() < e2.getCId())
							s.add(new Tuple(e1, e2));
		}
	}
}
