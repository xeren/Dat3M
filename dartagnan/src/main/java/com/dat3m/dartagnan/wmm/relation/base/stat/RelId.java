package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.stream.Stream;

public class RelId extends StaticRelation {

	public RelId() {
		term = "id";
	}

	@Override
	protected void update(ProgramCache p, TupleSet s){
		for(Event v: p.cache(FilterBasic.get(EType.VISIBLE)))
			s.add(new Tuple(v, v));
	}

	@Override
	protected Stream<Clause> termFO(Counter t, int a, int b) {
		return Stream.of(Clause.eq(a, b));
	}
}
