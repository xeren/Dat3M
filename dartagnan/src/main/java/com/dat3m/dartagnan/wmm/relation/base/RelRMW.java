package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.svcomp.event.EndAtomic;
import com.dat3m.dartagnan.program.event.rmw.RMWStore;
import com.dat3m.dartagnan.program.arch.aarch64.utils.EType;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterIntersection;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Flag;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import java.util.LinkedList;

public class RelRMW extends StaticRelation {

	private final FilterAbstract loadFilter = FilterIntersection.get(
		FilterBasic.get(EType.EXCL),
		FilterBasic.get(EType.READ)
	);

	private final FilterAbstract storeFilter = FilterIntersection.get(
		FilterBasic.get(EType.EXCL),
		FilterBasic.get(EType.WRITE)
	);

	// Set without exclusive events
	private TupleSet baseMaxTupleSet;

	public RelRMW() {
		term = "rmw";
		forceDoEncode = true;
	}

	@Override
	public void initialise() {
		super.initialise();
		this.baseMaxTupleSet = null;
	}

	@Override
	protected void update(ProgramCache p, TupleSet s){
		// atomic Read-Modify-Write pairs are atomic
		for(Event store: p.cache(FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.WRITE))))
			if(store instanceof RMWStore)
				baseMaxTupleSet.add(new Tuple(((RMWStore) store).getLoadEvent(), store));

		// mutex locks and unlocks are atomic
		for(Event x: p.cache(FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.LOCK)))) {
			if(x instanceof Load) {
				com.dat3m.dartagnan.program.event.Event next = ((Load)x).getSuccessor();
				Event nnext = next.getSuccessor();
				baseMaxTupleSet.add(new Tuple(x, next));
				baseMaxTupleSet.add(new Tuple(x, nnext));
				baseMaxTupleSet.add(new Tuple(next, nnext));
			}
		}

		// explicit atomic sections are atomic
		for(Event end: p.cache(FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.ATOMIC)))) {
			// TODO: why some non EndAtomic events match the ATOMIC filter?
			// The check below should not be necessary, but better to have
			// in case some other event might get ATOMIC tag in the future
			if(!(end instanceof EndAtomic))
				continue;
			for(com.dat3m.dartagnan.program.event.Event b: ((EndAtomic) end).getBlock()) {
				com.dat3m.dartagnan.program.event.Event next = b.getSuccessor();
				while(next != null && !(next instanceof EndAtomic)) {
					baseMaxTupleSet.add(new Tuple(b, next));
					next = next.getSuccessor();
				}
				baseMaxTupleSet.add(new Tuple(b, end));
			}
		}

		// all but the below tuples are to be encoded regularly
		s.addAll(baseMaxTupleSet);

		// ARM exclusive pairs are atomic
		for(ProgramCache.Thread thread: p.thread())
			for(Event load: thread.cache(loadFilter))
				for(Event store: thread.cache(storeFilter))
					if(load.getCId() < store.getCId())
						s.add(new Tuple(load, store));
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p, Atom atom) {
		// Encode base (not exclusive pairs) RMW
		TupleSet origEncodeTupleSet = encodeTupleSet;
		encodeTupleSet = baseMaxTupleSet;
		super.encodeApprox(e, p, atom);
		encodeTupleSet = origEncodeTupleSet;

		// Encode RMW for exclusive pairs
		LinkedList<BoolExpr> unpredictable = new LinkedList<>();
		for(ProgramCache.Thread thread: p.thread()) {
			for(Event store: thread.cache(storeFilter)) {
				LinkedList<BoolExpr> storeExec = new LinkedList<>();
				for(Event load: thread.cache(loadFilter)) {
					if(load.getCId() < store.getCId()) {

						// Encode if load and store form an exclusive pair
						BoolExpr isPair = e.context.mkBoolConst("excl(" + load.getCId() + "," + store.getCId() + ")");
						e.rule(e.eq(isPair, e.and(
							e.exec(load),
							e.cf(store),
							e.and(thread.cache(loadFilter).stream()
								.filter(o->o.getCId() > load.getCId() && o.getCId() < store.getCId())
								.map(e::exec).map(e::not)),
							e.and(thread.cache(storeFilter).stream()
								.filter(o->o.getCId() > load.getCId() && o.getCId() < store.getCId())
								.map(e::cf).map(e::not)))));

						// If load and store have the same address
						BoolExpr sameAddress = e.eq(((MemEvent) load).getAddress().toZ3Int(load, e), (((MemEvent) store).getAddress().toZ3Int(store, e)));
						unpredictable.add(e.and(isPair, e.exec(store), e.not(sameAddress)));

						// Relation between exclusive load and store
						e.rule(e.eq(atom.of(load, store), e.and(isPair, e.exec(store), sameAddress)));

						// Can be executed if addresses mismatch, but behaviour is "constrained unpredictable"
						// The implementation does not include all possible unpredictable cases: in case of address
						// mismatch, addresses of read and write are unknown, i.e. read and write can use any address
						storeExec.add(isPair);
					}
				}
				// if an exclusive store is executed, there is a fitting exclusive load beforehand
				e.rule(e.implies(e.exec(store), e.or(storeExec)));
			}
		}
		e.rule(e.eq(Flag.ARM_UNPREDICTABLE_BEHAVIOUR.repr(e.context), e.or(unpredictable)));
	}

}
