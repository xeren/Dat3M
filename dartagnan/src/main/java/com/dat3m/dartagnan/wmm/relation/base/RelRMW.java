package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.svcomp.event.EndAtomic;
import com.dat3m.dartagnan.program.event.rmw.RMWStore;
import com.dat3m.dartagnan.program.arch.aarch64.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterIntersection;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Flag;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

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
	public void initialise(Program program, Context ctx, Settings settings) {
		super.initialise(program, ctx, settings);
		this.baseMaxTupleSet = null;
	}

	@Override
	public TupleSet getMaxTupleSet() {
		if(maxTupleSet == null) {
			baseMaxTupleSet = new TupleSet();
			FilterAbstract filter = FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.WRITE));
			for(Event store: program.getCache().getEvents(filter)) {
				if(store instanceof RMWStore) {
					baseMaxTupleSet.add(new Tuple(((RMWStore) store).getLoadEvent(), store));
				}
			}

			filter = FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.LOCK));
			for(Event e: program.getCache().getEvents(filter)) {
				if(e instanceof Load) {
					Event next = e.getSuccessor();
					Event nnext = next.getSuccessor();
					baseMaxTupleSet.add(new Tuple(e, next));
					baseMaxTupleSet.add(new Tuple(e, nnext));
					baseMaxTupleSet.add(new Tuple(next, nnext));
				}
			}

			filter = FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.ATOMIC));
			for(Event end: program.getCache().getEvents(filter)) {
				// TODO: why some non EndAtomic events match the ATOMIC filter?
				// The check below should not be necessary, but better to have
				// in case some other event might get ATOMIC tag in the future
				if(!(end instanceof EndAtomic)) {
					continue;
				}
				for(Event b: ((EndAtomic) end).getBlock()) {
					Event next = b.getSuccessor();
					while(next != null && !(next instanceof EndAtomic)) {
						baseMaxTupleSet.add(new Tuple(b, next));
						next = next.getSuccessor();
					}
					baseMaxTupleSet.add(new Tuple(b, end));
				}
			}

			maxTupleSet = new TupleSet();
			maxTupleSet.addAll(baseMaxTupleSet);

			for(Thread thread: program.getThreads()) {
				for(Event load: thread.getCache().getEvents(loadFilter)) {
					for(Event store: thread.getCache().getEvents(storeFilter)) {
						if(load.getCId() < store.getCId()) {
							maxTupleSet.add(new Tuple(load, store));
						}
					}
				}
			}
		}
		return maxTupleSet;
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
		for(Thread thread: program.getThreads()) {
			for(Event store: thread.getCache().getEvents(storeFilter)) {
				LinkedList<BoolExpr> storeExec = new LinkedList<>();
				for(Event load: thread.getCache().getEvents(loadFilter)) {
					if(load.getCId() < store.getCId()) {

						// Encode if load and store form an exclusive pair
						BoolExpr isPair = e.context.mkBoolConst("excl(" + load.getCId() + "," + store.getCId() + ")");
						BoolExpr isExecPair = e.and(isPair, store.exec());
						LinkedList<BoolExpr> pairingCond = new LinkedList<>();
						pairingCond.add(load.exec());
						pairingCond.add(store.cf());

						for(Event otherLoad: thread.getCache().getEvents(loadFilter))
							if(otherLoad.getCId() > load.getCId() && otherLoad.getCId() < store.getCId())
								pairingCond.add(e.not(otherLoad.exec()));
						for(Event otherStore: thread.getCache().getEvents(storeFilter))
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
