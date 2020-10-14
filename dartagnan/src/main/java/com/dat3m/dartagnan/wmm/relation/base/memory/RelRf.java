package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import java.util.*;
import java.util.stream.Stream;

public class RelRf extends Relation {

	//FIXME
	public static boolean FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY = false;
	public static boolean FLAG_USE_SEQ_ENCODING_REL_RF = true;

	public RelRf() {
		term = "rf";
		forceDoEncode = true;
	}

	@Override
	public void update(ProgramCache p, TupleSet s){
		List<Event> eventsLoad = p.cache(FilterBasic.get(EType.READ));
		for(Event e1: p.cache(FilterBasic.get(EType.WRITE)))
			for(Event e2: eventsLoad)
				if(MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent) e2))
					s.add(new Tuple(e1, e2));
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		Map<MemEvent,LinkedList<BoolExpr>> edgeMap = new HashMap<>();
		Map<MemEvent,LinkedList<BoolExpr>> memInitMap = new HashMap<>();

		boolean canAccNonInitMem = FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY;

		for(Tuple tuple: maxTupleSet) {
			MemEvent w = (MemEvent) tuple.getFirst();
			MemEvent r = (MemEvent) tuple.getSecond();
			BoolExpr edge = e.edge(this, w, r);
			BoolExpr sameAddress = e.eq(w.getAddress().toZ3Int(w, e), r.getAddress().toZ3Int(r, e));
			BoolExpr sameValue = e.eq(w.getMemValueExpr(e), r.getMemValueExpr(e));

			edgeMap.putIfAbsent(r, new LinkedList<>());
			edgeMap.get(r).add(edge);
			if(canAccNonInitMem && w.is(EType.INIT)) {
				memInitMap.putIfAbsent(r, new LinkedList<>());
				memInitMap.get(r).add(sameAddress);
			}
			e.rule(e.implies(edge, e.and(e.exec(w), e.exec(r), sameAddress, sameValue)));
		}

		if(FLAG_USE_SEQ_ENCODING_REL_RF) {
			for(MemEvent r: edgeMap.keySet()) {
				List<BoolExpr> edges = edgeMap.get(r);
				int num = edges.size();
				int readId = r.getCId();
				BoolExpr lastSeqVar = mkSeqVar(e, readId, 0);
				LinkedList<BoolExpr> atMostOne = new LinkedList<>();
				atMostOne.add(e.eq(lastSeqVar, edges.get(0)));

				for(int i = 1; i < num; i++) {
					BoolExpr newSeqVar = mkSeqVar(e, readId, i);
					atMostOne.add(e.eq(newSeqVar, e.or(lastSeqVar, edges.get(i))));
					atMostOne.add(e.or(e.not(edges.get(i)), e.not(lastSeqVar)));
					lastSeqVar = newSeqVar;
				}
				BoolExpr atLeastOne = e.or(lastSeqVar, edges.get(edges.size() - 1));
				e.rule(e.and(atMostOne));
				e.rule(e.implies(canAccNonInitMem ? e.and(e.exec(r), e.or(memInitMap.get(r))) : e.exec(r), atLeastOne));
			}
		} else {
			for(MemEvent r: edgeMap.keySet()) {
				List<BoolExpr> edges = edgeMap.get(r);
				LinkedList<BoolExpr> atMostOne = new LinkedList<>();
				LinkedList<BoolExpr> atLeastOne = new LinkedList<>();
				for(int i = 0; i < edges.size(); i++) {
					atLeastOne.add(edges.get(i));
					for(int j = i + 1; j < edges.size(); j++)
						atMostOne.add(e.or(e.not(edges.get(i)), e.not(edges.get(j))));
				}
				e.rule(e.and(atMostOne));
				e.rule(e.implies(canAccNonInitMem ? e.and(e.exec(r), e.or(memInitMap.get(r))) : e.exec(r), e.or(atLeastOne)));
			}
		}
	}

	private BoolExpr mkSeqVar(EncodeContext e, int readId, int i) {
		return e.context.mkBoolConst("s(" + term + ",E" + readId + "," + i + ")");
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		FilterBasic read = FilterBasic.get(EType.READ);
		FilterBasic write = FilterBasic.get(EType.WRITE);
		read.encodeFO(e, p);
		write.encodeFO(e, p);

		List<Event> eventsLoad = p.cache(read);
		EncodeContext.BinaryPredicate edge = e.binary(getName());
		Expr a = e.bind(0);
		Expr b = e.bind(1);
		Expr c = e.bind(2);

		// match
		e.ruleForall(List.of(a, b), List.of(edge.of(a, b)),
			e.or(p.cache(write).stream().map(MemEvent.class::cast)
				.map(w->e.and(
					e.exec(w),
					e.eq(a, e.event(w)),
					e.or(eventsLoad.stream().map(MemEvent.class::cast)
						.map(r->e.and(
							e.exec(r),
							e.eq(b, e.event(r)),
							e.eq(w.getAddress().toZ3Int(w, e), r.getAddress().toZ3Int(r, e)),
							e.eq(w.getMemValueExpr(e), r.getMemValueExpr(e)))))))));

		// TODO satisfaction

		// singleness
		e.ruleForall(List.of(a, b, c), List.of(edge.of(a, c), edge.of(b, c)), e.eq(a, b));
	}

	@Override
	protected Stream<Clause> termFO(Counter t, int a, int b) {
		return Stream.of(Clause.edge(term, a, b));
	}
}
