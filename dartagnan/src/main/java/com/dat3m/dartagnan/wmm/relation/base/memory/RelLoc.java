package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;

public class RelLoc extends Relation {

	public RelLoc() {
		term = "loc";
	}

	@Override
	protected void update(ProgramCache p, TupleSet s){
		List<Event> events = p.cache(FilterBasic.get(EType.MEMORY));
		for(Event e1: events)
			for(Event e2: events)
				if(e1.getCId() != e2.getCId() && MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent) e2))
					s.add(new Tuple(e1, e2));
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		for(Tuple tuple: encodeTupleSet)
			e.rule(e.eq(e.edge(this, tuple), e.and(
				e.exec(tuple.getFirst()),
				e.exec(tuple.getSecond()),
				e.eq(
					((MemEvent)tuple.getFirst()).getAddress().toZ3Int(tuple.getFirst(), e),
					((MemEvent)tuple.getSecond()).getAddress().toZ3Int(tuple.getSecond(), e)))));
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		//TODO restrict to M*M
		EncodeContext.RelationPredicate edge = e.of(this);
		List<Event> events = p.cache(FilterBasic.get(EType.MEMORY));
		e.rule(e.and(events.stream().map(MemEvent.class::cast).flatMap(a->events.stream().map(MemEvent.class::cast)
			.filter(b->a.getCId() != b.getCId())
			.filter(b->MemEvent.canAddressTheSameLocation(a, b))
			.map(b->e.eq(
				edge.of(e.event(a), e.event(b)),
				e.and(e.exec(a), e.exec(b), e.eq(a.getAddress().toZ3Int(a, e), b.getAddress().toZ3Int(b, e))))))));
	}
}
