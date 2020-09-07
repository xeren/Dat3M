package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;

import java.util.Map;
import java.util.Set;

/**
 * @author Florian Furbach
 */
public class Acyclic extends Axiom {

	public Acyclic(Relation rel) {
		super(rel);
	}

	public Acyclic(Relation rel, boolean negate) {
		super(rel, negate);
	}

	@Override
	public TupleSet getEncodeTupleSet(ProgramCache p) {
		Map<Event,Set<Event>> transMap = rel.getMaxTupleSet(p).transMap();
		TupleSet result = new TupleSet();

		for(Event e1: transMap.keySet()) {
			if(transMap.get(e1).contains(e1)) {
				for(Event e2: transMap.get(e1)) {
					if(e2.getCId() != e1.getCId() && transMap.get(e2).contains(e1)) {
						result.add(new Tuple(e1, e2));
					}
				}
			}
		}

		for(Tuple tuple: rel.getMaxTupleSet(p)) {
			if(tuple.getFirst().getCId() == tuple.getSecond().getCId()) {
				result.add(tuple);
			}
		}

		result.retainAll(rel.getMaxTupleSet(p));
		return result;
	}

	@Override
	protected BoolExpr _consistent(EncodeContext e) {
		String name = rel.getName();
		return e.and(rel.getEncodeTupleSet().stream()
			.map(tuple->{
				Event e1 = tuple.getFirst();
				Event e2 = tuple.getSecond();
				IntExpr i1 = e.intVar(name, e1);
				IntExpr i2 = e.intVar(name, e2);
				return e.and(
					e.implies(e1.exec(), e.lt(e.zero(), i1)),
					e.implies(e.edge(name, e1, e2), e.lt(i1, i2)));
			}));
	}

	@FunctionalInterface
	private interface CycleVar {
		BoolExpr of(Event event);
	}

	@Override
	protected BoolExpr _inconsistent(EncodeContext e) {
		String name = rel.getName();
		String cycleName = "Cycle:" + name;
		CycleVar cycleVar = event->e.context.mkBoolConst("Cycle(" + event.repr() + ")(" + name + ")");
		return e.and(
			e.and(rel.getEncodeTupleSet().stream()
				.map(t->e.implies(
					e.edge(cycleName, t),
					e.and(
						t.getFirst().exec(),
						t.getSecond().exec(),
						e.edge(name, t),
						cycleVar.of(t.getFirst()),
						cycleVar.of(t.getSecond()))))),
			e.and(rel.getEncodeTupleSet().stream()
				.map(Tuple::getFirst)
				.distinct()
				.map(t->e.implies(cycleVar.of(t), e.and(
					e.or(rel.getEncodeTupleSet().getByFirst(t).stream()
						.map(Tuple::getSecond)
						.map(t1->e.and(
							e.edge(cycleName, t, t1),
							e.and(rel.getEncodeTupleSet().getByFirst(t).stream()
								.map(Tuple::getSecond)
								.filter(t2->t1.getCId() != t2.getCId())
								.map(t2->e.not(e.edge(cycleName, t, t2))))))),
					e.or(rel.getEncodeTupleSet().getBySecond(t).stream()
						.map(Tuple::getFirst)
						.map(t1->e.and(
							e.edge(cycleName, t1, t),
							e.and(rel.getEncodeTupleSet().getBySecond(t).stream()
								.map(Tuple::getFirst)
								.filter(t2->t1.getCId() != t2.getCId())
								.map(t2->e.not(e.edge(cycleName, t2, t))))))))))),
			e.or(rel.getEncodeTupleSet().stream()
				.map(Tuple::getFirst)
				.distinct()
				.map(cycleVar::of)));
	}

	@Override
	protected String _toString() {
		return "acyclic " + rel.getName();
	}
}
