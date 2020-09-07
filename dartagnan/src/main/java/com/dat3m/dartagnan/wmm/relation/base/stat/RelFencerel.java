package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class RelFencerel extends Relation {

	private final String fenceName;

	public static String makeTerm(String fenceName) {
		return "fencerel(" + fenceName + ")";
	}

	public RelFencerel(String fenceName) {
		this.fenceName = fenceName;
		term = makeTerm(fenceName);
	}

	public RelFencerel(String fenceName, String name) {
		super(name);
		this.fenceName = fenceName;
		term = makeTerm(fenceName);
	}

	@Override
	protected void update(ProgramCache p, TupleSet s){
		for(ProgramCache.Thread t: p.thread()) {
			List<Event> fences = t.cache(FilterBasic.get(fenceName));
			if(!fences.isEmpty()) {
				List<Event> events = t.cache(FilterBasic.get(EType.MEMORY));
				ListIterator<Event> it1 = events.listIterator();
				while(it1.hasNext()) {
					Event e1 = it1.next();
					ListIterator<Event> it2 = events.listIterator(it1.nextIndex());
					while(it2.hasNext()) {
						Event e2 = it2.next();
						for(Event f: fences) {
							if(f.getCId() > e1.getCId() && f.getCId() < e2.getCId()) {
								s.add(new Tuple(e1, e2));
								break;
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		List<Event> fences = p.cache(FilterBasic.get(fenceName));
		for(Tuple tuple: encodeTupleSet) {
			Event e1 = tuple.getFirst();
			Event e2 = tuple.getSecond();

			LinkedList<BoolExpr> orClause = new LinkedList<>();
			for(Event fence: fences)
				if(fence.getCId() > e1.getCId() && fence.getCId() < e2.getCId())
					orClause.add(fence.exec());

			e.rule(e.eq(e.edge(this, e1, e2), e.and(e1.exec(), e2.exec(), e.or(orClause))));
		}
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		EncodeContext.RelationPredicate edge = e.of(this);
		List<Event> fences = p.cache(FilterBasic.get(fenceName));
		e.rule(e.forall(0,
			(a, b) -> e.eq(edge.of(a, b), e.or(fences.stream().map(f -> e.and(f.exec(),
				e.lt((ArithExpr) a, (ArithExpr) e.event(f)),
				e.lt((ArithExpr) e.event(f), (ArithExpr) b)
			)))),
			fences.stream().map(f -> (EncodeContext.BinaryPattern) (a, b) -> e.pattern(f.exec(),
				e.lt((ArithExpr) a, (ArithExpr) e.event(f)),
				e.lt((ArithExpr) e.event(f), (ArithExpr) b)
			)).toArray(EncodeContext.BinaryPattern[]::new)));
	}
}
