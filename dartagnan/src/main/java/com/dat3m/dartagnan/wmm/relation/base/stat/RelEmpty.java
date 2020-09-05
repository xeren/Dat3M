package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelEmpty extends Relation {

	public RelEmpty(String name) {
		super(name);
		term = name;
	}

	@Override
	public TupleSet getMaxTupleSet() {
		if (maxTupleSet == null)
			maxTupleSet = new TupleSet();
		return maxTupleSet;
	}

	@Override
	protected void encodeApprox(EncodeContext context) {
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e) {
		EncodeContext.RelationPredicate edge = e.of(this);
		e.rule(e.forall(0, (a,b)->e.not(edge.of(a, b)),
			(a,b)->e.pattern(edge.of(a, b))));
	}
}
