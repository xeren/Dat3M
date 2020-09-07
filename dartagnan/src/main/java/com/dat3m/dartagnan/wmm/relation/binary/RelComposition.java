package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.*;

/**
 * @author Florian Furbach
 */
public class RelComposition extends BinaryRelation {

	public static String makeTerm(Relation r1, Relation r2) {
		return "(" + r1.getName() + ";" + r2.getName() + ")";
	}

	public RelComposition(Relation r1, Relation r2) {
		super(r1, r2);
		term = makeTerm(r1, r2);
	}

	public RelComposition(Relation r1, Relation r2, String name) {
		super(r1, r2, name);
		term = makeTerm(r1, r2);
	}

	@Override
	protected void update(ProgramCache p, TupleSet s, TupleSet s1, TupleSet s2) {
		for(Tuple t1: s1)
			for(Tuple t2: s2.getByFirst(t1.getSecond()))
				s.add(new Tuple(t1.getFirst(), t2.getSecond()));
	}

	@Override
	public void addEncodeTupleSet(ProgramCache p, TupleSet s) {
		Set<Tuple> activeSet = new HashSet<>(s);
		activeSet.removeAll(encodeTupleSet);
		encodeTupleSet.addAll(s);
		activeSet.retainAll(maxTupleSet);

		if(!activeSet.isEmpty()) {
			TupleSet r1Set = new TupleSet();
			TupleSet r2Set = new TupleSet();

			Map<Integer, Set<Integer>> myMap = new HashMap<>();
			for(Tuple tuple: activeSet) {
				int id1 = tuple.getFirst().getCId();
				int id2 = tuple.getSecond().getCId();
				myMap.putIfAbsent(id1, new HashSet<>());
				myMap.get(id1).add(id2);
			}

			for(Tuple tuple1: r1.getMaxTupleSet(p)) {
				Event e1 = tuple1.getFirst();
				Set<Integer> ends = myMap.get(e1.getCId());
				if(ends == null) continue;
				for(Tuple tuple2: r2.getMaxTupleSet(p).getByFirst(tuple1.getSecond())) {
					Event e2 = tuple2.getSecond();
					if(ends.contains(e2.getCId())) {
						r1Set.add(tuple1);
						r2Set.add(tuple2);
					}
				}
			}

			r1.addEncodeTupleSet(p, r1Set);
			r2.addEncodeTupleSet(p, r2Set);
		}
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		TupleSet r1Set = new TupleSet();
		r1Set.addAll(r1.getEncodeTupleSet());
		r1Set.retainAll(r1.getMaxTupleSet(p));

		TupleSet r2Set = new TupleSet();
		r2Set.addAll(r2.getEncodeTupleSet());
		r2Set.retainAll(r2.getMaxTupleSet(p));

		Map<Tuple,LinkedList<BoolExpr>> exprMap = new HashMap<>();
		for(Tuple tuple: encodeTupleSet)
			exprMap.put(tuple, new LinkedList<>());

		for(Tuple tuple1: r1Set) {
			Event e1 = tuple1.getFirst();
			Event e3 = tuple1.getSecond();
			for(Tuple tuple2: r2Set.getByFirst(e3)) {
				Event e2 = tuple2.getSecond();
				Tuple id = new Tuple(e1, e2);
				if(exprMap.containsKey(id))
					exprMap.get(id).add(e.and(e.edge(r1, e1, e3), e.edge(r2, e3, e2)));
			}
		}

		for(Tuple tuple: encodeTupleSet)
			e.rule(e.eq(e.edge(this, tuple), e.or(exprMap.get(tuple))));
	}

	@Override
	protected void encodeIDL(EncodeContext e, ProgramCache p) {
		if(recursiveGroupId == 0)
		{
			encodeApprox(e, p);
			return;
		}

		boolean recurseInR1 = (r1.getRecursiveGroupId() & recursiveGroupId) > 0;
		boolean recurseInR2 = (r2.getRecursiveGroupId() & recursiveGroupId) > 0;

		TupleSet r1Set = new TupleSet();
		r1Set.addAll(r1.getEncodeTupleSet());
		r1Set.retainAll(r1.getMaxTupleSet(p));

		TupleSet r2Set = new TupleSet();
		r2Set.addAll(r2.getEncodeTupleSet());
		r2Set.retainAll(r2.getMaxTupleSet(p));

		Map<Tuple, LinkedList<BoolExpr>> orClauseMap = new HashMap<>();
		Map<Tuple, LinkedList<BoolExpr>> idlClauseMap = new HashMap<>();
		for(Tuple tuple: encodeTupleSet) {
			orClauseMap.put(tuple, new LinkedList<>());
			idlClauseMap.put(tuple, new LinkedList<>());
		}

		for(Tuple tuple1: r1Set) {
			Event e1 = tuple1.getFirst();
			Event e3 = tuple1.getSecond();
			for(Tuple tuple2: r2Set.getByFirst(e3)) {
				Event e2 = tuple2.getSecond();
				Tuple id = new Tuple(e1, e2);
				if(orClauseMap.containsKey(id)) {
					BoolExpr opt1 = e.edge(r1, e1, e3);
					BoolExpr opt2 = e.edge(r2, e3, e2);
					orClauseMap.get(id).add(e.and(opt1, opt2));

					if(recurseInR1)
						opt1 = e.and(opt1, e.lt(e.intCount(r1, e1, e2), e.intCount(this, e1, e3)));
					if(recurseInR2)
						opt2 = e.and(opt2, e.lt(e.intCount(r1, e1, e2), e.intCount(this, e3, e2)));
					idlClauseMap.get(id).add(e.and(opt1, opt2));
				}
			}
		}

		e.rule(e.and(encodeTupleSet.stream().map(t->e.and(
			e.eq(e.edge(this, t), e.or(orClauseMap.get(t))),
			e.eq(e.edge(this, t), e.or(idlClauseMap.get(t)))))));
	}

	@Override
	public void encodeIteration(EncodeContext e, ProgramCache p, int groupId, int iteration) {
		if((groupId & recursiveGroupId) <= 0 || iteration <= lastEncodedIteration)
			return;
		lastEncodedIteration = iteration;

		if(iteration == 0 && isRecursive) {
			for(Tuple tuple: encodeTupleSet)
				e.rule(e.not(e.edge(this, iteration, tuple)));
			return;
		}

		int childIteration = isRecursive ? iteration - 1 : iteration;

		boolean recurseInR1 = (r1.getRecursiveGroupId() & groupId) > 0;
		boolean recurseInR2 = (r2.getRecursiveGroupId() & groupId) > 0;

		java.util.function.BiFunction<?super Event,?super Event,?extends BoolExpr> edge1
			= recurseInR1 ? (x,y)->e.edge(r1, childIteration, x, y) : (x,y)->e.edge(r1, x, y);
		java.util.function.BiFunction<?super Event,?super Event,?extends BoolExpr> edge2
			= recurseInR2 ? (x,y)->e.edge(r2, childIteration, x, y) : (x,y)->e.edge(r2, x, y);

		TupleSet r1Set = new TupleSet();
		r1Set.addAll(r1.getEncodeTupleSet());
		r1Set.retainAll(r1.getMaxTupleSet(p));

		TupleSet r2Set = new TupleSet();
		r2Set.addAll(r2.getEncodeTupleSet());
		r2Set.retainAll(r2.getMaxTupleSet(p));

		Map<Tuple, LinkedList<BoolExpr>> exprMap = new HashMap<>();
		for(Tuple tuple: encodeTupleSet)
			exprMap.put(tuple, new LinkedList<>());

		for(Tuple tuple1: r1Set) {
			Event e1 = tuple1.getFirst();
			Event e3 = tuple1.getSecond();
			for(Tuple tuple2: r2Set.getByFirst(e3)) {
				Event e2 = tuple2.getSecond();
				Tuple id = new Tuple(e1, e2);
				if(exprMap.containsKey(id))
					exprMap.get(id).add(e.and(edge1.apply(e1, e3), edge2.apply(e3, e2)));
			}
		}

		for(Tuple tuple: encodeTupleSet)
			e.rule(e.eq(e.edge(this, iteration, tuple), e.or(exprMap.get(tuple))));

		if(recurseInR1)
			r1.encodeIteration(e, p, groupId, childIteration);

		if(recurseInR2)
			r2.encodeIteration(e, p, groupId, childIteration);

	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		EncodeContext.RelationPredicate edge = e.of(this);
		EncodeContext.RelationPredicate edge1 = e.of(r1);
		EncodeContext.RelationPredicate edge2 = e.of(r2);
		e.rule(e.forall(0, (a,c)->e.eq(edge.of(a, c),
				e.exists(2, b->e.and(edge1.of(a, b), edge2.of(b, c)),
					b->e.pattern(edge1.of(a, b), edge2.of(b, c)))),
			(a,c)->e.pattern(edge.of(a, c))));
	}
}
