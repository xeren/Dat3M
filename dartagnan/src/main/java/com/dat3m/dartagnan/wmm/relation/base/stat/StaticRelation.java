package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;

public abstract class StaticRelation extends Relation {

	public StaticRelation() {
		super();
	}

	public StaticRelation(String name) {
		super(name);
	}

	@FunctionalInterface
	protected interface Atom {
		BoolExpr of(Event first, Event second);
		default BoolExpr of(Tuple tuple) {
			return of(tuple.getFirst(), tuple.getSecond());
		}
	}

	protected void encodeApprox(EncodeContext e, Atom atom) {
		e.rule(e.and(encodeTupleSet.stream().map(t->e.eq(atom.of(t), e.and(t.getFirst().exec(), t.getSecond().exec())))));
	}

	@Override
	protected void encodeApprox(EncodeContext context) {
		encodeApprox(context, (a,b)->context.edge(this, a, b));
	}

	@Override
	protected void encodeFirstOrder(EncodeContext context) {
		EncodeContext.RelationPredicate edge = context.of(this);
		encodeApprox(context, (a,b)->edge.of(context.event(a), context.event(b)));
	}
}
