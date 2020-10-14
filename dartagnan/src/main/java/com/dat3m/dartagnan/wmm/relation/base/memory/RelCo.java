package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterMinus;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class RelCo extends Relation {

	public RelCo() {
		term = "co";
		forceDoEncode = true;
	}

	@Override
	public void update(ProgramCache p, TupleSet s) {
		List<Event> eventsInit = p.cache(FilterBasic.get(EType.INIT));
		List<Event> eventsStore = p.cache(FilterMinus.get(
			FilterBasic.get(EType.WRITE),
			FilterBasic.get(EType.INIT)
		));

		for(Event e1: eventsInit)
			for(Event e2: eventsStore)
				if(MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent) e2))
					s.add(new Tuple(e1, e2));

		for(Event e1: eventsStore)
			for(Event e2: eventsStore)
				if(e1.getCId() != e2.getCId() && MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent) e2))
					s.add(new Tuple(e1, e2));
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		String name = getName();

		List<Event> eventsInit = p.cache(FilterBasic.get(EType.INIT));
		List<Event> eventsStore = p.cache(FilterMinus.get(FilterBasic.get(EType.WRITE), FilterBasic.get(EType.INIT)));

		for(Event i: eventsInit) {
			e.rule(e.eq(e.intVar(name, i), e.zero()));
		}

		List<IntExpr> intVars = new ArrayList<>();
		for(Event w: eventsStore) {
			IntExpr coVar = e.intVar(name, w);
			e.rule(e.lt(e.zero(), coVar));
			intVars.add(coVar);
		}
		e.rule(e.distinct(intVars));

		for(Event w: p.cache(FilterBasic.get(EType.WRITE))) {
			MemEvent w1 = (MemEvent) w;
			LinkedList<BoolExpr> lastCo = new LinkedList<>();
			lastCo.add(e.exec(w1));

			for(Tuple t: maxTupleSet.getByFirst(w1)) {
				MemEvent w2 = (MemEvent) t.getSecond();
				BoolExpr relation = e.edge(this, w1, w2);
				lastCo.add(e.not(relation));

				e.rule(e.eq(relation, e.and(
					e.exec(w1),
					e.exec(w2),
					e.eq(w1.getAddress().toZ3Int(w1, e), w2.getAddress().toZ3Int(w2, e)),
					e.lt(e.intVar(name, w1), e.intVar(name, w2)))));
			}

			BoolExpr lastCoExpr = e.context.mkBoolConst("co_last(" + w1.getCId() + ")");
			e.rule(e.eq(lastCoExpr, e.and(lastCo)));

			for(Address address: w1.getMaxAddressSet()) {
				e.rule(e.implies(
					e.and(lastCoExpr, e.eq(w1.getAddress().toZ3Int(w1, e), address.toZ3Int(e))),
					e.eq(address.getLastMemValueExpr(e), w1.getMemValueExpr(e))));
			}
		}
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		FilterBasic init = FilterBasic.get(EType.INIT);
		FilterBasic write = FilterBasic.get(EType.WRITE);
		init.encodeFO(e, p);
		write.encodeFO(e, p);

		List<Event> eventsInit = p.cache(init);
		List<Event> eventsWrite = p.cache(write);
		List<Event> eventsStore = p.cache(FilterMinus.get(write, init));
		EncodeContext.BinaryPredicate edge = e.binary(getName());
		Expr a = e.bind(0);
		Expr b = e.bind(1);
		Expr c = e.bind(2);

		// necessity
		e.ruleForall(List.of(a, b), List.of(edge.of(a, b)),
			e.or(eventsWrite.stream().map(MemEvent.class::cast).map(v -> e.and(
				e.exec(v),
				e.eq(a, e.event(v)),
				e.or(eventsStore.stream()
					// already implied by asymmetric, but shortens the formula
					.filter(w->v.getCId() != w.getCId())
					.map(MemEvent.class::cast)
					.map(w->e.and(
						e.exec(w),
						e.eq(b, e.event(w)),
						// pair has same address
						e.eq(v.getAddress().toZ3Int(v, e), w.getAddress().toZ3Int(w, e)))))))));

		// TODO sufficiency
		//e.ruleForall(List.of(a, b), List.of(e.unary("W").of(a), e.unary("W").of(b)), e.or(e.not(e.eq(e.address(a), e.address(b))), edge.of(a, b), edge.of(b, a)));

		// asymmetry
		e.ruleForall(List.of(a, b), List.of(edge.of(a, b), edge.of(b, a)), e.or());

		// transitivity
		e.ruleForall(List.of(a, b, c), List.of(edge.of(a, b), edge.of(b, c)), edge.of(a, c));

		// initial writes always appear first for any address.
		eventsInit.stream().map(MemEvent.class::cast)
			.flatMap(v->eventsStore.stream().map(MemEvent.class::cast)
				.map(w->e.implies(e.eq(v.getAddress().toZ3Int(v, e), w.getAddress().toZ3Int(w, e)), edge.of(e.event(v), e.event(w)))))
			.forEach(e::rule);
	}

	@Override
	protected Stream<Clause> termFO(Counter t, int a, int b) {
		return Stream.of(Clause.edge(term, a, b));
	}
}
