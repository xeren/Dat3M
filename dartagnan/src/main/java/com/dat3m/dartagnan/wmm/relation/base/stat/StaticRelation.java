package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;

import java.util.stream.Stream;

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

	protected void encodeApprox(EncodeContext context, ProgramCache program, Atom atom) {
		context.rule(context.and(encodeTupleSet.stream()
			.map(t->context.eq(atom.of(t), context.and(context.exec(t.getFirst()), context.exec(t.getSecond()))))));
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		encodeApprox(e, p, (a,b)->e.edge(this, a, b));
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		EncodeContext.BinaryPredicate edge = e.binary(getName());
		encodeApprox(e, p, (a,b)->edge.of(e.event(a), e.event(b)));
	}

	@Override
	protected Stream<Clause> termFO(Counter c, int x, int y) {
		return Stream.of(Clause.edge(getName(), x, y));
	}
}
