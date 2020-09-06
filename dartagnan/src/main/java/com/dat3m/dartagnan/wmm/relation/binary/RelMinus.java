package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

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
	public void initialise(Program program, Context ctx, Settings settings) {
		super.initialise(program, ctx, settings);
		if(r2.getRecursiveGroupId() > 0) {
			throw new RuntimeException("Relation " + r2.getName() + " cannot be recursive since it occurs in a set minus.");
		}
	}

	@Override
	public void update(EncodeContext e, TupleSet s, TupleSet s1, TupleSet s2) {
		s.addAll(s1);
	}

	@Override
	protected void encodeApprox(EncodeContext e) {
		e.rule(e.and(encodeTupleSet.stream().map(Relation.PostFixApprox
			? t->e.or(e.edge(this, t), e.not(e.edge(r1, t)), e.edge(r2, t))
			: t->e.eq(e.edge(this, t), e.and(e.edge(r1, t), e.not(e.edge(r2, t)))))));
	}

	@Override
	protected void encodeIDL(EncodeContext e) {
		if(recursiveGroupId == 0) {
			encodeApprox(e);
			return;
		}

		e.rule(e.and(encodeTupleSet.stream().map(t->e.eq(e.edge(this, t), e.and(
			e.edge(r1, t),
			e.not(e.edge(r2, t)),
			e.lt(e.intCount(r1, t), e.intCount(this, t)))))));
	}

	@Override
	public void encodeIteration(EncodeContext e, int groupId, int iteration) {
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
			r1.encodeIteration(e, groupId, childIteration);
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e) {
		EncodeContext.RelationPredicate edge = e.of(this);
		EncodeContext.RelationPredicate edge1 = e.of(r1);
		EncodeContext.RelationPredicate edge2 = e.of(r2);
		e.rule(e.forall(0, (a,b)->e.eq(edge.of(a, b), e.and(edge1.of(a, b), e.not(edge2.of(a, b)))),
			(a,b)->e.pattern(edge.of(a, b)),
			(a,b)->e.pattern(edge1.of(a, b), edge2.of(a, b))));
	}
}
