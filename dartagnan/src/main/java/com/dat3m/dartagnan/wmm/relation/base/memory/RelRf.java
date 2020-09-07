package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterMinus;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import java.util.*;

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
		List<Event> eventsInit = p.cache(FilterBasic.get(EType.INIT));
		List<Event> eventsStore = p.cache(FilterMinus.get(FilterBasic.get(EType.WRITE), FilterBasic.get(EType.INIT)));

		for(Event e1: eventsInit)
			for(Event e2: eventsLoad)
				if(MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent) e2))
					s.add(new Tuple(e1, e2));

		for(Event e1: eventsStore)
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
			BoolExpr sameAddress = e.eq(w.getMemAddressExpr(), r.getMemAddressExpr());
			BoolExpr sameValue = e.eq(w.getMemValueExpr(), r.getMemValueExpr());

			edgeMap.putIfAbsent(r, new LinkedList<>());
			edgeMap.get(r).add(edge);
			if(canAccNonInitMem && w.is(EType.INIT)) {
				memInitMap.putIfAbsent(r, new LinkedList<>());
				memInitMap.get(r).add(sameAddress);
			}
			e.rule(e.implies(edge, e.and(w.exec(), r.exec(), sameAddress, sameValue)));
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
				e.rule(e.implies(canAccNonInitMem ? e.and(r.exec(), e.or(memInitMap.get(r))) : r.exec(), atLeastOne));
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
				e.rule(e.implies(canAccNonInitMem ? e.and(r.exec(), e.or(memInitMap.get(r))) : r.exec(), e.or(atLeastOne)));
			}
		}
	}

	private BoolExpr mkSeqVar(EncodeContext e, int readId, int i) {
		return e.context.mkBoolConst("s(" + term + ",E" + readId + "," + i + ")");
	}

	@Override
	protected void encodeFirstOrder(EncodeContext e, ProgramCache p) {
		EncodeContext.RelationPredicate edge = e.of(this);
		e.rule(e.forall(0, (a,b)->e.implies(edge.of(a, b), e.or(
				maxTupleSet.stream().map(t->e.and(
					t.getFirst().exec(),
					t.getSecond().exec(),
					e.eq(a, e.event(t.getFirst())),
					e.eq(b, e.event(t.getSecond())),
					e.eq(((MemEvent) t.getFirst()).getMemAddressExpr(), ((MemEvent) t.getSecond()).getMemAddressExpr()),
					e.eq(((MemEvent) t.getFirst()).getMemValueExpr(), ((MemEvent) t.getSecond()).getMemValueExpr()))))),
			(a,b)->e.pattern(edge.of(a, b))));
		e.rule(e.and(p.cache(FilterBasic.get(EType.READ)).stream()
			.map(r->e.exists(0, w->edge.of(w, e.event(r))))));
		e.rule(e.forall(0, (a,b,c)->e.implies(e.and(edge.of(a, c), edge.of(b, c)), e.eq(a, b)),
			(a,b,c)->e.pattern(edge.of(a, c), edge.of(b, c))));
	}
}
