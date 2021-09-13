package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.ADDRDIRECT;

import java.util.*;

public class RelAddrDirect extends BasicRegRelation {

	public RelAddrDirect() {
		term = ADDRDIRECT;
		forceDoEncode = true;
	}

	@Override
	Collection<Event> getEvents() {
		return task.getProgram().getCache().getEvents(FilterBasic.get(EType.MEMORY));
	}

	@Override
	Collection<Register> getRegisters(Event e) {
		return e instanceof MemEvent ? ((MemEvent)e).getAddress().getRegs() : List.of();
	}
}
