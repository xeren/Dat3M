package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
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
	protected void update(TupleSet s){
		List<Event> events = program.getCache().getEvents(FilterBasic.get(EType.MEMORY));
		for(Event e1: events)
			for(Event e2: events)
				if(e1.getCId() != e2.getCId() && MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent) e2))
					s.add(new Tuple(e1, e2));
	}

	@Override
	protected void encodeApprox(EncodeContext e) {
		for(Tuple tuple: encodeTupleSet)
			e.rule(e.eq(e.edge(this, tuple), e.and(
				tuple.getFirst().exec(),
				tuple.getSecond().exec(),
				e.eq(
					((MemEvent) tuple.getFirst()).getMemAddressExpr(),
					((MemEvent) tuple.getSecond()).getMemAddressExpr()))));
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e) {
		//TODO restrict to M*M
		EncodeContext.RelationPredicate edge = e.of(this);
		List<Event> events = program.getCache().getEvents(FilterBasic.get(EType.MEMORY));
		e.rule(e.and(events.stream().map(MemEvent.class::cast).flatMap(a->events.stream().map(MemEvent.class::cast)
			.filter(b->a.getCId() != b.getCId())
			.filter(b->MemEvent.canAddressTheSameLocation(a, b))
			.map(b->e.eq(
				edge.of(e.event(a), e.event(b)),
				e.and(a.exec(), b.exec(), e.eq(a.getMemAddressExpr(), b.getMemAddressExpr())))))));
	}
}
