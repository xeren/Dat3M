package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;

import java.util.*;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;

public class RelRf extends Relation {

	public RelRf() {
		term = "rf";
		forceDoEncode = true;
	}

	@Override
	public TupleSet getMaxTupleSet() {
		if(maxTupleSet == null) {
			maxTupleSet = new TupleSet();

			List<Load> eventsLoad = program.getCache().getEvents(Load.class);
			List<Init> eventsInit = program.getCache().getEvents(Init.class);
			List<Store> eventsStore = program.getCache().getEvents(Store.class);

			for(Init e1 : eventsInit) {
				for(Load e2 : eventsLoad) {
					if(MemEvent.canAddressTheSameLocation(e1, e2)) {
						maxTupleSet.add(new Tuple(e1, e2));
					}
				}
			}

			for(Store e1 : eventsStore) {
				for(Load e2 : eventsLoad) {
					if(MemEvent.canAddressTheSameLocation(e1, e2)) {
						maxTupleSet.add(new Tuple(e1, e2));
					}
				}
			}
		}
		return maxTupleSet;
	}

	@Override
	protected BoolExpr encodeApprox() {
		BoolExpr enc = ctx.mkTrue();
		Map<MemEvent, List<BoolExpr>> edgeMap = new HashMap<>();
		Map<MemEvent, BoolExpr> memInitMap = new HashMap<>();

		boolean canAccNonInitMem = settings.getFlag(Settings.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY);
		boolean useSeqEncoding = settings.getFlag(Settings.FLAG_USE_SEQ_ENCODING_REL_RF);

		for(Tuple tuple : maxTupleSet) {
			MemEvent w = (MemEvent) tuple.getFirst();
			MemEvent r = (MemEvent) tuple.getSecond();
			BoolExpr edge = edge(term, w, r, ctx);

			IntExpr a1 = w.getMemAddressExpr().isBV() ? ctx.mkBV2Int((BitVecExpr) w.getMemAddressExpr(), false) : (IntExpr) w.getMemAddressExpr();
			IntExpr a2 = r.getMemAddressExpr().isBV() ? ctx.mkBV2Int((BitVecExpr) r.getMemAddressExpr(), false) : (IntExpr) r.getMemAddressExpr();
			BoolExpr sameAddress = ctx.mkEq(a1, a2);

			IntExpr v1 = w.getMemValueExpr().isBV() ? ctx.mkBV2Int((BitVecExpr) w.getMemValueExpr(), false) : (IntExpr) w.getMemValueExpr();
			IntExpr v2 = r.getMemValueExpr().isBV() ? ctx.mkBV2Int((BitVecExpr) r.getMemValueExpr(), false) : (IntExpr) r.getMemValueExpr();
			BoolExpr sameValue = ctx.mkEq(v1, v2);

			edgeMap.putIfAbsent(r, new ArrayList<>());
			edgeMap.get(r).add(edge);
			if(canAccNonInitMem && w instanceof Init) {
				memInitMap.put(r, ctx.mkOr(memInitMap.getOrDefault(r, ctx.mkFalse()), sameAddress));
			}
			enc = ctx.mkAnd(enc, ctx.mkImplies(edge, ctx.mkAnd(w.exec(), r.exec(), sameAddress, sameValue)));
		}

		for(MemEvent r : edgeMap.keySet()) {
			enc = ctx.mkAnd(enc, useSeqEncoding
				? encodeEdgeSeq(r, memInitMap.get(r), edgeMap.get(r))
				: encodeEdgeNaive(r, memInitMap.get(r), edgeMap.get(r)));
		}
		return enc;
	}

	private BoolExpr encodeEdgeNaive(Event read, BoolExpr isMemInit, List<BoolExpr> edges) {
		int num = edges.size();
		ArrayList<BoolExpr> atMostOne = new ArrayList<>();
		ArrayList<BoolExpr> atLeastOne = new ArrayList<>();
		atLeastOne.add(ctx.mkNot(read.exec()));
		for(int i = 0; i < num; i++) {
			BoolExpr edge = edges.get(i);
			atLeastOne.add(edge);
			for(int j = i + 1; j < num; j++) {
				atMostOne.add(ctx.mkNot(ctx.mkAnd(edge, edges.get(j))));
			}
		}
		if(settings.getFlag(Settings.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY)) {
			atLeastOne.add(ctx.mkNot(isMemInit));
		}
		atMostOne.add(ctx.mkOr(atLeastOne.toArray(new BoolExpr[0])));
		return ctx.mkAnd(atMostOne.toArray(new BoolExpr[0]));
	}

	private BoolExpr encodeEdgeSeq(Event read, BoolExpr isMemInit, List<BoolExpr> edges) {
		int num = edges.size() - 1;
		int readId = read.getCId();
		ArrayList<BoolExpr> atMostOne = new ArrayList<>();
		BoolExpr lastSeqVar = edges.get(0);
		for(int i = 1; i < num; i++) {
			atMostOne.add(ctx.mkNot(ctx.mkAnd(edges.get(i), lastSeqVar)));
			BoolExpr newSeqVar = ctx.mkBoolConst("rf seq " + readId + " " + i);
			atMostOne.add(ctx.mkEq(newSeqVar, ctx.mkOr(lastSeqVar, edges.get(i))));
			lastSeqVar = newSeqVar;
		}
		BoolExpr atLeastOne = ctx.mkOr(ctx.mkNot(read.exec()), lastSeqVar, edges.get(edges.size() - 1));
		if(settings.getFlag(Settings.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY)) {
			atMostOne.add(ctx.mkImplies(isMemInit, atLeastOne));
		} else {
			atMostOne.add(atLeastOne);
		}
		return ctx.mkAnd(atMostOne.toArray(new BoolExpr[0]));
	}
}
