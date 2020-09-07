package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.HashSet;
import java.util.Set;

public class RelRangeIdentity extends UnaryRelation {

	public static String makeTerm(Relation r1) {
		return "[range(" + r1.getName() + ")]";
	}

	public RelRangeIdentity(Relation r1) {
		super(r1);
		term = makeTerm(r1);
	}

	public RelRangeIdentity(Relation r1, String name) {
		super(r1, name);
		term = makeTerm(r1);
	}

	@Override
	protected void update(ProgramCache p, TupleSet s, TupleSet s1) {
		for(Tuple tuple: s1)
			s.add(new Tuple(tuple.getSecond(), tuple.getSecond()));
	}

	@Override
	public void addEncodeTupleSet(ProgramCache p, TupleSet tuples) {
		encodeTupleSet.addAll(tuples);
		Set<Tuple> activeSet = new HashSet<>(tuples);
		activeSet.retainAll(maxTupleSet);
		if(!activeSet.isEmpty()) {
			TupleSet r1Set = new TupleSet();
			for(Tuple tuple: activeSet) {
				r1Set.addAll(r1.getMaxTupleSet(p).getBySecond(tuple.getFirst()));
			}
			r1.addEncodeTupleSet(p, r1Set);
		}
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		for(Tuple tuple: encodeTupleSet) {
			Event a = tuple.getFirst();
			e.rule(e.eq(e.edge(this, a, a),
				e.or(r1.getMaxTupleSet(p).getBySecond(a).stream().map(t->e.edge(r1, t.getFirst(), a)))));
		}
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		EncodeContext.RelationPredicate edge = e.of(this);
		EncodeContext.RelationPredicate edge1 = e.of(r1);
		e.rule(e.forall(0, (a, b)->e.eq(edge.of(a, b), e.and(
				e.eq(a, b),
				e.exists(2, c->edge1.of(c, b), c->e.pattern(edge1.of(c, b))))),
			(a,b)->e.pattern(edge.of(a, b))));
		e.rule(e.forall(0, (a,b)->e.implies(edge1.of(a, b), edge.of(b, b)),
			(a,b)->e.pattern(edge1.of(a, b))));
	}
}
