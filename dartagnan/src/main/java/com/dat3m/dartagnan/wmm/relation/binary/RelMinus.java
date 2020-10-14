package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import java.util.stream.Stream;

/**
 * @author Florian Furbach
 */
public class RelMinus extends BinaryRelation {

	public static String makeTerm(Relation r1, Relation r2) {
		return "(" + r1.getName() + "\\" + r2.getName() + ")";
	}

	public RelMinus(Relation r1, Relation r2) {
		super(r1, r2);
		term = makeTerm(r1, r2);
	}

	public RelMinus(Relation r1, Relation r2, String name) {
		super(r1, r2, name);
		term = makeTerm(r1, r2);
	}

	@Override
	public void initialise() {
		super.initialise();
		if(r2.getRecursiveGroupId() > 0) {
			throw new RuntimeException("Relation " + r2.getName() + " cannot be recursive since it occurs in a set minus.");
		}
	}

	@Override
	public void update(ProgramCache p, TupleSet s, TupleSet s1, TupleSet s2) {
		s.addAll(s1);
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		e.rule(e.and(encodeTupleSet.stream().map(Relation.PostFixApprox
			? t->e.or(e.edge(this, t), e.not(e.edge(r1, t)), e.edge(r2, t))
			: t->e.eq(e.edge(this, t), e.and(e.edge(r1, t), e.not(e.edge(r2, t)))))));
	}

	@Override
	protected void encodeIDL(EncodeContext e, ProgramCache p) {
		if(recursiveGroupId == 0) {
			encodeApprox(e, p);
			return;
		}

		e.rule(e.and(encodeTupleSet.stream().map(t->e.eq(e.edge(this, t), e.and(
			e.edge(r1, t),
			e.not(e.edge(r2, t)),
			e.lt(e.intCount(r1, t), e.intCount(this, t)))))));
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
		boolean recurse = (r1.getRecursiveGroupId() & groupId) > 0;
		assert (r2.getRecursiveGroupId() & groupId) == 0;

		java.util.function.Function<?super Tuple,?extends BoolExpr> edge
			= recurse ? t->e.edge(r1, childIteration, t) : t->e.edge(r1, t);

		for(Tuple tuple: encodeTupleSet)
			e.rule(e.eq(e.edge(this, iteration, tuple), e.and(edge.apply(tuple), e.not(e.edge(r2, tuple)))));

		if(recurse)
			r1.encodeIteration(e, p, groupId, childIteration);
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		// enables name
		super.encodeFirstOrder(e, p);

		// encoding of the negation
		int[] counter = new int[]{2};
		r2.nameFO(()->counter[0]++, 0, 1).forEach(c->consumeFO(e, e.binaryNot("(not " + r2.getName() + ")"), counter[0], c));
	}

	@Override
	protected Stream<Clause> termFO(Counter t, int a, int b) {
		Clause edge = Clause.edge("(not " + r2.getName() + ")", a, b);
		return r1.nameFO(t, a, b).map(edge::combine);
	}
}
