package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.Map;
import java.util.Set;

/**
 * @author Florian Furbach
 */
public class RelTransRef extends RelTrans {

	private TupleSet identityEncodeTupleSet = new TupleSet();
	private TupleSet transEncodeTupleSet = new TupleSet();

	public static String makeTerm(Relation r1) {
		return r1.getName() + "^*";
	}

	public RelTransRef(Relation r1) {
		super(r1);
		term = makeTerm(r1);
	}

	public RelTransRef(Relation r1, String name) {
		super(r1, name);
		term = makeTerm(r1);
	}

	@Override
	public void initialise(Program program, Context ctx, Settings settings) {
		super.initialise(program, ctx, settings);
		identityEncodeTupleSet = new TupleSet();
		transEncodeTupleSet = new TupleSet();
	}

	@Override
	protected void update(TupleSet s, TupleSet s1) {
		super.update(s, s1);
		for(Map.Entry<Event, Set<Event>> entry: transitiveReachabilityMap.entrySet())
			entry.getValue().remove(entry.getKey());
		for(Event e: program.getCache().getEvents(FilterBasic.get(EType.ANY)))
			s.add(new Tuple(e, e));
	}

	@Override
	public void addEncodeTupleSet(TupleSet tuples) {
		TupleSet activeSet = new TupleSet();
		activeSet.addAll(tuples);
		activeSet.removeAll(encodeTupleSet);
		encodeTupleSet.addAll(activeSet);
		activeSet.retainAll(maxTupleSet);

		for(Tuple tuple: activeSet) {
			if(tuple.getFirst().getCId() == tuple.getSecond().getCId()) {
				identityEncodeTupleSet.add(tuple);
			}
		}
		activeSet.removeAll(identityEncodeTupleSet);

		TupleSet temp = encodeTupleSet;
		encodeTupleSet = transEncodeTupleSet;
		super.addEncodeTupleSet(activeSet);
		encodeTupleSet = temp;
	}

	@Override
	protected void encodeApprox(EncodeContext context) {
		invokeEncode(context, super::encodeApprox);
	}

	@Override
	protected void encodeIDL(EncodeContext context) {
		invokeEncode(context, super::encodeIDL);
	}

	@Override
	protected void encodeLFP(EncodeContext context) {
		invokeEncode(context, super::encodeLFP);
	}

	@FunctionalInterface
	private interface Encoder {
		void encode(EncodeContext context);
	}

	private void invokeEncode(EncodeContext context, Encoder encoder) {
		TupleSet temp = encodeTupleSet;
		encodeTupleSet = transEncodeTupleSet;
		encoder.encode(context);
		encodeTupleSet = temp;
		for(Tuple tuple: identityEncodeTupleSet)
			context.rule(context.edge(this, tuple.getFirst(), tuple.getFirst()));
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e) {
		EncodeContext.RelationPredicate edge = e.of(this);
		EncodeContext.RelationPredicate edge1 = e.of(r1);
		e.rule(e.forall(0, (a,c)->e.or(e.not(edge.of(a, c)), e.eq(a, c), edge1.of(a, c),
				e.exists(2, b->e.and(edge.of(a, b), edge.of(b, c)),
					b->e.pattern(edge.of(a, b), edge.of(b, c)))),
			(a,c)->e.pattern(edge.of(a, c))));
		e.rule(e.forall(0, a->edge.of(a, a), e::pattern));
		e.rule(e.forall(0, (a,c)->e.implies(edge1.of(a, c), edge.of(a, c)),
			(a,c)->e.pattern(edge1.of(a, c))));
		e.rule(e.forall(0, (a,b,c)->e.implies(e.and(edge.of(a, b), edge.of(b, c)), edge.of(a, c)),
			(a,b,c)->e.pattern(edge.of(a, b), edge.of(b, c))));
	}
}