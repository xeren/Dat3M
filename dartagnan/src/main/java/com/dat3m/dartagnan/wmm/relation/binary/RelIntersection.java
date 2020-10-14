package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @author Florian Furbach
 */
public class RelIntersection extends BinaryRelation {

	public static String makeTerm(Relation r1, Relation r2) {
		return "(" + r1.getName() + "&" + r2.getName() + ")";
	}

	public RelIntersection(Relation r1, Relation r2) {
		super(r1, r2);
		term = makeTerm(r1, r2);
	}

	public RelIntersection(Relation r1, Relation r2, String name) {
		super(r1, r2, name);
		term = makeTerm(r1, r2);
	}

	@Override
	public void update(ProgramCache p, TupleSet s, TupleSet s1, TupleSet s2) {
		s.addAll(s1);
		s.retainAll(s2);
	}

	@Override
	public void encodeApprox(EncodeContext e, ProgramCache p) {
		e.rule(e.and(encodeTupleSet.stream()
			.map(t->e.eq(e.edge(this, t), e.and(e.edge(r1, t), e.edge(r2, t))))));
	}

	@Override
	protected void encodeIDL(EncodeContext e, ProgramCache p) {
		if(recursiveGroupId == 0) {
			encodeApprox(e, p);
			return;
		}

		boolean recurseInR1 = (r1.getRecursiveGroupId() & recursiveGroupId) > 0;
		boolean recurseInR2 = (r2.getRecursiveGroupId() & recursiveGroupId) > 0;

		for(Tuple tuple: encodeTupleSet) {
			BoolExpr opt1 = e.edge(r1, tuple);
			BoolExpr opt2 = e.edge(r2, tuple);
			e.rule(e.eq(e.edge(this, tuple), e.and(opt1, opt2)));

			if(recurseInR1)
				opt1 = e.and(opt1, e.lt(e.intCount(r1, tuple), e.intCount(this, tuple)));
			if(recurseInR2)
				opt2 = e.and(opt2, e.lt(e.intCount(r2, tuple), e.intCount(this, tuple)));
			e.rule(e.eq(e.edge(this, tuple), e.and(opt1, opt2)));
		}
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

		java.util.function.Function<? super Tuple, ? extends BoolExpr> edge1
			= recurseInR1 ? t->e.edge(r1, childIteration, t) : t->e.edge(r1, t);
		java.util.function.Function<? super Tuple, ? extends BoolExpr> edge2
			= recurseInR2 ? t->e.edge(r2, childIteration, t) : t->e.edge(r2, t);

		for(Tuple tuple: encodeTupleSet)
			e.rule(e.eq(e.edge(this, iteration, tuple), e.and(edge1.apply(tuple), edge2.apply(tuple))));

		if(recurseInR1)
			r1.encodeIteration(e, p, groupId, childIteration);

		if(recurseInR2)
			r2.encodeIteration(e, p, groupId, childIteration);
	}

	@Override
	protected Stream<Clause> termFO(Counter t, int a, int b) {
		Clause[] f = r1.nameFO(t, a, b).toArray(Clause[]::new);
		return r2.nameFO(t, a, b).flatMap(s->Arrays.stream(f).map(s::combine));
	}
}
