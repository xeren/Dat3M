package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelEmpty extends Relation {

	public RelEmpty(String name) {
		super(name);
		term = name;
	}

	@Override
	protected void update(ProgramCache p, TupleSet s) {
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		EncodeContext.RelationPredicate edge = e.of(this);
		e.rule(e.forall(0, (a,b)->e.not(edge.of(a, b)),
			(a,b)->e.pattern(edge.of(a, b))));
	}
}
