package com.dat3m.porthos;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterUnion;
import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.Store;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.*;
import static java.util.stream.Stream.concat;

class Encodings {

	static BoolExpr encodeCommonExecutions(EncodeContext context, Program p1, Program p2) {
		FilterUnion f = FilterUnion.get(FilterBasic.get(EType.MEMORY), FilterBasic.get(EType.LOCAL));
		List<Event> p1Events = p1.getCache().getEvents(f);
		List<Event> p2Events = p2.getCache().getEvents(f);

		Iterator<Event> it1 = p1Events.iterator();
		Iterator<Event> it2 = p2Events.iterator();

		Set<Tuple> rTuples = new TupleSet();
		Set<Tuple> wTuples = new TupleSet();

		LinkedList<BoolExpr> enc = new LinkedList<>();

		while(it1.hasNext() && it2.hasNext()) {
			Event e1 = it1.next();
			Event e2 = it2.next();

			if(e1.getUId() != e2.getUId()) {
				throw new RuntimeException("Invalid unrolled Id");
			}
			enc.add(context.eq(context.exec(e1), context.exec(e2)));

			if(e1 instanceof Load && e2 instanceof Load) {
				rTuples.add(new Tuple(e1, e2));

			} else if(e1 instanceof Store && e2 instanceof Store) {
				wTuples.add(new Tuple(e1, e2));
			}
		}

		for(Tuple rTuple: rTuples) {
			Event r1 = rTuple.getFirst();
			Event r2 = rTuple.getSecond();
			for(Tuple wTuple: wTuples) {
				enc.add(context.eq(
					context.edge("rf", wTuple.getFirst(), r1),
					context.edge("rf", wTuple.getSecond(), r2)
				));
			}
		}

		for(Tuple wTupleFrom: wTuples) {
			Event w1From = wTupleFrom.getFirst();
			Event w2From = wTupleFrom.getSecond();
			for(Tuple wTupleTo: wTuples) {
				if(w1From.getCId() != wTupleTo.getFirst().getCId()) {
					enc.add(context.eq(
						context.edge("co", w1From, wTupleTo.getFirst()),
						context.edge("co", w2From, wTupleTo.getSecond())
					));
				}
			}
		}
		return context.and(enc);
	}

	static BoolExpr encodeReachedState(Program p, Model model, EncodeContext c) {
		return c.and(
			concat(
				p.getLocations().stream(),
				p.getCache().getEvents(FilterBasic.get(EType.ANY)).stream()
					.filter(e->model.getConstInterp(c.exec(e)).isTrue())
					.filter(RegWriter.class::isInstance).map(RegWriter.class::cast)
					.map(RegWriter::getResultRegister))
			.distinct()
			.map(l->l.getLastValueExpr(c))
			.map(x->c.eq(x, model.getConstInterp(x))));
	}
}