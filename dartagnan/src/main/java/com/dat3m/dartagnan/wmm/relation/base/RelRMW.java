package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.svcomp.event.EndAtomic;
import com.dat3m.dartagnan.program.event.rmw.RMWStore;
import com.dat3m.dartagnan.program.arch.aarch64.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterIntersection;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
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
	protected void update(EncodeContext e, TupleSet s){
		FilterAbstract filter = FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.WRITE));
		for(Event store: e.cache(filter))
			if(store instanceof RMWStore)
				baseMaxTupleSet.add(new Tuple(((RMWStore) store).getLoadEvent(), store));

		filter = FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.LOCK));
		for(Event x: e.cache(filter)) {
			if(x instanceof Load) {
				Event next = x.getSuccessor();
				Event nnext = next.getSuccessor();
				baseMaxTupleSet.add(new Tuple(x, next));
				baseMaxTupleSet.add(new Tuple(x, nnext));
				baseMaxTupleSet.add(new Tuple(next, nnext));
			}
		}

		filter = FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.ATOMIC));
		for(Event end: e.cache(filter)) {
			// TODO: why some non EndAtomic events match the ATOMIC filter?
			// The check below should not be necessary, but better to have
			// in case some other event might get ATOMIC tag in the future
			if(!(end instanceof EndAtomic))
				continue;
			for(Event b: ((EndAtomic) end).getBlock()) {
				Event next = b.getSuccessor();
				while(next != null && !(next instanceof EndAtomic)) {
					baseMaxTupleSet.add(new Tuple(b, next));
					next = next.getSuccessor();
				}
				baseMaxTupleSet.add(new Tuple(b, end));
			}
		}

		s.addAll(baseMaxTupleSet);

		for(EncodeContext.Thread thread: e.thread())
			for(Event load: thread.cache(loadFilter))
				for(Event store: thread.cache(storeFilter))
					if(load.getCId() < store.getCId())
						s.add(new Tuple(load, store));
	}

	@Override
	protected void encodeApprox(EncodeContext e, Atom atom) {
		// Encode base (not exclusive pairs) RMW
		TupleSet origEncodeTupleSet = encodeTupleSet;
		encodeTupleSet = baseMaxTupleSet;
		super.encodeApprox(e, atom);
		encodeTupleSet = origEncodeTupleSet;

		// Encode RMW for exclusive pairs
		LinkedList<BoolExpr> unpredictable = new LinkedList<>();
		for(EncodeContext.Thread thread: e.thread()) {
			for(Event store: thread.cache(storeFilter)) {
				LinkedList<BoolExpr> storeExec = new LinkedList<>();
				for(Event load: thread.cache(loadFilter)) {
					if(load.getCId() < store.getCId()) {

						// Encode if load and store form an exclusive pair
						BoolExpr isPair = e.context.mkBoolConst("excl(" + load.getCId() + "," + store.getCId() + ")");
						BoolExpr isExecPair = e.and(isPair, store.exec());
						LinkedList<BoolExpr> pairingCond = new LinkedList<>();
						pairingCond.add(load.exec());
						pairingCond.add(store.cf());

						for(Event otherLoad: thread.cache(loadFilter))
							if(otherLoad.getCId() > load.getCId() && otherLoad.getCId() < store.getCId())
								pairingCond.add(e.not(otherLoad.exec()));
						for(Event otherStore: thread.cache(storeFilter))
							if(otherStore.getCId() > load.getCId() && otherStore.getCId() < store.getCId())
								pairingCond.add(e.not(otherStore.cf()));
						e.rule(e.eq(isPair, e.and(pairingCond)));

						// If load and store have the same address
						BoolExpr sameAddress = e.eq(((MemEvent) load).getMemAddressExpr(), (((MemEvent) store).getMemAddressExpr()));
						unpredictable.add(e.and(isExecPair, e.not(sameAddress)));

						// Relation between exclusive load and store
						e.rule(e.eq(atom.of(load, store), e.and(isExecPair, sameAddress)));

						// Can be executed if addresses mismatch, but behaviour is "constrained unpredictable"
						// The implementation does not include all possible unpredictable cases: in case of address
						// mismatch, addresses of read and write are unknown, i.e. read and write can use any address
						storeExec.add(isPair);
					}
				}
				e.rule(e.implies(store.exec(), e.or(storeExec)));
			}
		}
		e.rule(e.eq(Flag.ARM_UNPREDICTABLE_BEHAVIOUR.repr(e.context), e.or(unpredictable)));
	}

}
