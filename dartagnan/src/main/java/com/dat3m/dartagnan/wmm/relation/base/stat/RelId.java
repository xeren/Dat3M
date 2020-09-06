package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelId extends StaticRelation {

	public RelId() {
		term = "id";
	}

	@Override
	protected void update(EncodeContext e, TupleSet s){
		for(Event v: e.cache(FilterBasic.get(EType.VISIBLE)))
			s.add(new Tuple(v, v));
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e) {
		EncodeContext.RelationPredicate edge = e.of(this);
		e.rule(e.forall(0, (a,b)->e.implies(edge.of(a, b), e.eq(a, b)), (a,b)->e.pattern(edge.of(a, b))));
		e.rule(e.forall(0, a->edge.of(a, a), e::pattern));
	}
}
