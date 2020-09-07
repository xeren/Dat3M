package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.HashSet;
import java.util.Set;

public class RelDomainIdentity extends UnaryRelation {

	public static String makeTerm(Relation r1) {
		return "[domain(" + r1.getName() + ")]";
	}

	public RelDomainIdentity(Relation r1) {
		super(r1);
		term = makeTerm(r1);
	}

	public RelDomainIdentity(Relation r1, String name) {
		super(r1, name);
		term = makeTerm(r1);
	}

	@Override
	protected void update(ProgramCache p, TupleSet s, TupleSet s1) {
		for(Tuple tuple: s1)
			s.add(new Tuple(tuple.getFirst(), tuple.getFirst()));
	}

	@Override
	public void addEncodeTupleSet(ProgramCache p, TupleSet tuples) {
		encodeTupleSet.addAll(tuples);
		Set<Tuple> activeSet = new HashSet<>(tuples);
		activeSet.retainAll(maxTupleSet);
		if(!activeSet.isEmpty()) {
			TupleSet r1Set = new TupleSet();
			for(Tuple tuple: activeSet)
				r1Set.addAll(r1.getMaxTupleSet(p).getByFirst(tuple.getFirst()));
			r1.addEncodeTupleSet(p, r1Set);
		}
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		for(Tuple tuple: encodeTupleSet) {
			Event event = tuple.getFirst();
			e.rule(e.eq(e.edge(this, event, event),
				e.or(r1.getMaxTupleSet(p).getByFirst(event).stream().map(t->e.edge(r1, event, t.getSecond())))));
		}
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		EncodeContext.RelationPredicate edge = e.of(this);
		EncodeContext.RelationPredicate edge1 = e.of(r1);
		e.rule(e.forall(0, (a,b)->e.eq(edge.of(a, b), e.and(
				e.eq(a, b),
				e.exists(2, c->edge1.of(a, c), c->e.pattern(edge1.of(a, c))))),
			(a,b)->e.pattern(edge.of(a, b))));
		e.rule(e.forall(0, (a,b)->e.implies(edge1.of(a, b), edge.of(a, a)),
			(a,b)->e.pattern(edge1.of(a, b))));
	}
}
