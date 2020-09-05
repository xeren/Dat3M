package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.*;

/**
 * @author Florian Furbach
 */
public class RelTrans extends UnaryRelation {

	Map<Event, Set<Event>> transitiveReachabilityMap;
	private TupleSet fullEncodeTupleSet;

	public static String makeTerm(Relation r1) {
		return r1.getName() + "^+";
	}

	public RelTrans(Relation r1) {
		super(r1);
		term = makeTerm(r1);
	}

	public RelTrans(Relation r1, String name) {
		super(r1, name);
		term = makeTerm(r1);
	}

	@Override
	public void initialise(Program program, Context ctx, Settings settings) {
		super.initialise(program, ctx, settings);
		fullEncodeTupleSet = new TupleSet();
		transitiveReachabilityMap = null;
	}

	@Override
	public TupleSet getMaxTupleSet() {
		if(maxTupleSet == null) {
			transitiveReachabilityMap = r1.getMaxTupleSet().transMap();
			maxTupleSet = new TupleSet();
			for(Event e1: transitiveReachabilityMap.keySet()) {
				for(Event e2: transitiveReachabilityMap.get(e1)) {
					maxTupleSet.add(new Tuple(e1, e2));
				}
			}
		}
		return maxTupleSet;
	}

	@Override
	public void addEncodeTupleSet(TupleSet tuples) {
		TupleSet activeSet = new TupleSet();
		activeSet.addAll(tuples);
		activeSet.removeAll(encodeTupleSet);
		encodeTupleSet.addAll(activeSet);
		activeSet.retainAll(maxTupleSet);

		TupleSet fullActiveSet = getFullEncodeTupleSet(activeSet);
		if(fullEncodeTupleSet.addAll(fullActiveSet)) {
			fullActiveSet.retainAll(r1.getMaxTupleSet());
			r1.addEncodeTupleSet(fullActiveSet);
		}
	}

	@Override
	protected void encodeApprox(EncodeContext e) {
		for(Tuple tuple: fullEncodeTupleSet) {
			LinkedList<BoolExpr> orClause = new LinkedList<>();

			Event e1 = tuple.getFirst();
			Event e2 = tuple.getSecond();

			if(r1.getMaxTupleSet().contains(tuple))
				orClause.add(e.edge(r1, e1, e2));

			for(Event e3: transitiveReachabilityMap.get(e1))
				if(e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId() && transitiveReachabilityMap.get(e3).contains(e2))
					orClause.add(e.and(e.edge(this, e1, e3), e.edge(this, e3, e2)));

			e.rule(Relation.PostFixApprox
				? e.implies(e.or(orClause), e.edge(this, e1, e2))
				: e.eq(e.edge(this, e1, e2), e.or(orClause)));
		}
	}

	@Override
	protected void encodeIDL(EncodeContext e) {
		String nameConcat = "(" + getName() + ";" + getName() + ")";
		for(Tuple tuple: fullEncodeTupleSet) {
			Event e1 = tuple.getFirst();
			Event e2 = tuple.getSecond();
			BoolExpr edgeConcat = e.edge(nameConcat, e1, e2);
			IntExpr intCountConcat = e.intCount(nameConcat, e1, e2);

			LinkedList<BoolExpr> firstCondition = new LinkedList<>();
			LinkedList<BoolExpr> secondCondition = new LinkedList<>();
			for(Tuple tuple2: fullEncodeTupleSet.getByFirst(e1)) {
				Event e3 = tuple2.getSecond();
				if(!e2.equals(e3) && transitiveReachabilityMap.get(e3).contains(e2)) {
					firstCondition.add(e.and(
						e.edge(this, e1, e3),
						e.edge(this, e3, e2),
						e.lt(e.intCount(this, e1, e3), intCountConcat),
						e.lt(e.intCount(this, e3, e2), intCountConcat)));
					secondCondition.add(e.and(e.edge(this, e1, e3), e.edge(this, e3, e2)));
				}
			}

			e.rule(e.eq(edgeConcat, e.or(firstCondition)));
			e.rule(e.eq(edgeConcat, e.or(secondCondition)));
			e.rule(e.implies(edgeConcat, e.lt(intCountConcat, e.intCount(this, e1, e2))));
			e.rule(e.eq(e.edge(this, e1, e2), e.or(e.edge(r1, e1, e2), edgeConcat)));
		}
	}

	@Override
	protected void encodeLFP(EncodeContext e) {
		int iteration = 0;

		// Encode initial iteration
		Set<Tuple> currentTupleSet = new HashSet<>(r1.getEncodeTupleSet());
		for(Tuple tuple: currentTupleSet)
			e.rule(e.eq(e.edge(r1, iteration, tuple), e.edge(r1, tuple)));

		while(true) {
			HashMap<Tuple, LinkedList<BoolExpr>> currentTupleMap = new HashMap<>();
			HashSet<Tuple> newTupleSet = new HashSet<>();

			// Original tuples from the previous iteration
			for(Tuple tuple: currentTupleSet) {
				currentTupleMap.putIfAbsent(tuple, new LinkedList<>());
				currentTupleMap.get(tuple).add(e.edge(r1, iteration, tuple));
			}

			// Combine tuples from the previous iteration
			for(Tuple tuple1: currentTupleSet) {
				Event e1 = tuple1.getFirst();
				Event e3 = tuple1.getSecond();
				for(Tuple tuple2: currentTupleSet) {
					if(e3.getCId() == tuple2.getFirst().getCId()) {
						Event e2 = tuple2.getSecond();
						Tuple newTuple = new Tuple(e1, e2);
						currentTupleMap.putIfAbsent(newTuple, new LinkedList<>());
						currentTupleMap.get(newTuple).add(
							e.and(e.edge(r1, iteration, e1, e3), e.edge(r1, iteration, e3, e2)));

						if(newTuple.getFirst().getCId() != newTuple.getSecond().getCId())
							newTupleSet.add(newTuple);
					}
				}
			}

			iteration++;

			// Encode this iteration
			for(Tuple tuple: currentTupleMap.keySet())
				e.rule(e.eq(e.edge(r1, iteration, tuple), e.or(currentTupleMap.get(tuple))));

			if(!currentTupleSet.addAll(newTupleSet))
				break;
		}

		// Encode that transitive relation equals the relation at the last iteration
		for(Tuple tuple: encodeTupleSet)
			e.rule(e.eq(e.edge(this, tuple), e.edge(r1, iteration, tuple)));
	}

	private TupleSet getFullEncodeTupleSet(TupleSet tuples) {
		TupleSet processNow = new TupleSet();
		processNow.addAll(tuples);
		processNow.retainAll(getMaxTupleSet());

		TupleSet result = new TupleSet();

		while(!processNow.isEmpty()) {
			TupleSet processNext = new TupleSet();
			result.addAll(processNow);

			for(Tuple tuple: processNow) {
				Event e1 = tuple.getFirst();
				Event e2 = tuple.getSecond();
				for(Event e3: transitiveReachabilityMap.get(e1)) {
					if(e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId()
						&& transitiveReachabilityMap.get(e3).contains(e2)) {
						processNext.add(new Tuple(e1, e3));
						processNext.add(new Tuple(e3, e2));
					}
				}
			}
			processNext.removeAll(result);
			processNow = processNext;
		}
		return result;
	}

	protected void encodeFirstOrder(EncodeContext e) {
		EncodeContext.RelationPredicate edge = e.of(this);
		EncodeContext.RelationPredicate edge1 = e.of(r1);
		e.rule(e.forall(0, (a,c)->e.or(e.not(edge.of(a, c)), edge1.of(a, c),
				e.exists(2, b->e.and(edge.of(a, b), edge.of(b, c)),
					b->e.pattern(edge.of(a, b), edge.of(b, c)))),
			(a,c)->e.pattern(edge.of(a, c))));
		e.rule(e.forall(0, (a,c)->e.implies(edge1.of(a, c), edge.of(a, c)),
			(a,c)->e.pattern(edge1.of(a, c))));
		e.rule(e.forall(0, (a,b,c)->e.implies(e.and(edge.of(a, b), edge.of(b, c)), edge.of(a, c)),
			(a,b,c)->e.pattern(edge.of(a, b), edge.of(a, c))));
	}
}