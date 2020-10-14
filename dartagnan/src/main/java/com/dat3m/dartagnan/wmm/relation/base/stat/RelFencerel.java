package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

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
			int c1 = e1.getCId();
			int c2 = e2.getCId();
			e.rule(e.eq(e.edge(this, e1, e2), e.and(
				e.exec(e1),
				e.exec(e2),
				e.or(fences.stream().filter(f->f.getCId() > c1 && f.getCId() < c2).map(e::exec)))));
		}
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		//TODO avoid encodeTupleSet
		EncodeContext.BinaryPredicate edge = e.binary(getName());
		List<Event> fences = p.cache(FilterBasic.get(fenceName));
		for(Tuple tuple: encodeTupleSet) {
			Event e1 = tuple.getFirst();
			Event e2 = tuple.getSecond();
			int c1 = e1.getCId();
			int c2 = e2.getCId();
			e.rule(e.eq(edge.of(e.event(e1), e.event(e2)), e.and(
				e.exec(e1),
				e.exec(e2),
				e.or(fences.stream().filter(f->f.getCId() > c1 && f.getCId() < c2).map(e::exec)))));
		}
	}

	@Override
	protected Stream<Clause> termFO(Counter t, int a, int b) {
		return Stream.of(Clause.edge(term, a, b));
	}
}
