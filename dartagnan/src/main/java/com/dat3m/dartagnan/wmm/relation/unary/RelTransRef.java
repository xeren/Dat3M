package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.EncodeContext;
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
	public void initialise() {
		super.initialise();
		identityEncodeTupleSet = new TupleSet();
		transEncodeTupleSet = new TupleSet();
	}

	@Override
	protected void update(ProgramCache p, TupleSet s, TupleSet s1) {
		super.update(p, s, s1);
		for(Map.Entry<Event, Set<Event>> entry: transitiveReachabilityMap.entrySet())
			entry.getValue().remove(entry.getKey());
		for(Event x: p.cache(FilterBasic.get(EType.ANY)))
			s.add(new Tuple(x, x));
	}

	@Override
	public void addEncodeTupleSet(ProgramCache p, TupleSet s) {
		TupleSet activeSet = new TupleSet();
		activeSet.addAll(s);
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
		super.addEncodeTupleSet(p, activeSet);
		encodeTupleSet = temp;
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		invokeEncode(e, p, super::encodeApprox);
	}

	@Override
	protected void encodeIDL(EncodeContext e, ProgramCache p) {
		invokeEncode(e, p, super::encodeIDL);
	}

	@Override
	protected void encodeLFP(EncodeContext e, ProgramCache p) {
		invokeEncode(e, p, super::encodeLFP);
	}

	@FunctionalInterface
	private interface Encoder {
		void encode(EncodeContext context, ProgramCache program);
	}

	private void invokeEncode(EncodeContext context, ProgramCache program, Encoder encoder) {
		TupleSet temp = encodeTupleSet;
		encodeTupleSet = transEncodeTupleSet;
		encoder.encode(context, program);
		encodeTupleSet = temp;
		for(Tuple tuple: identityEncodeTupleSet)
			context.rule(context.edge(this, tuple.getFirst(), tuple.getFirst()));
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
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