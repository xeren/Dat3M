package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Florian Furbach
 */
public class RelInverse extends UnaryRelation {

	public static String makeTerm(Relation r1) {
		return r1.getName() + "^-1";
	}

	public RelInverse(Relation r1) {
		super(r1);
		term = makeTerm(r1);
	}

	public RelInverse(Relation r1, String name) {
		super(r1, name);
		term = makeTerm(r1);
	}

	@Override
	protected void update(ProgramCache p, TupleSet s, TupleSet s1) {
		for(Tuple pair: s1)
			s.add(new Tuple(pair.getSecond(), pair.getFirst()));
	}

	@Override
	public void addEncodeTupleSet(ProgramCache p, TupleSet tuples) {
		encodeTupleSet.addAll(tuples);
		Set<Tuple> activeSet = new HashSet<>(tuples);
		activeSet.retainAll(maxTupleSet);
		if(!activeSet.isEmpty()) {
			TupleSet invSet = new TupleSet();
			for(Tuple pair: activeSet) {
				invSet.add(new Tuple(pair.getSecond(), pair.getFirst()));
			}
			r1.addEncodeTupleSet(p, invSet);
		}
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		for(Tuple tuple: encodeTupleSet) {
			Event e1 = tuple.getFirst();
			Event e2 = tuple.getSecond();
			e.rule(e.eq(e.edge(this, e1, e2), e.edge(r1, e2, e1)));
		}
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		EncodeContext.RelationPredicate edge = e.of(this);
		EncodeContext.RelationPredicate edge1 = e.of(r1);
		e.rule(e.forall(0, (a,b)->e.eq(edge.of(a, b), edge1.of(b, a)),
			(a,b)->e.pattern(edge.of(a, b)),
			(a,b)->e.pattern(edge1.of(b, a))));
	}
}
    

