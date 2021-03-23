package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;

import java.util.*;

import static com.dat3m.dartagnan.utils.Settings.FLAG_CURRENT_MODEL_LOCAL_RF;

public class RelRf extends Relation {

    public RelRf(){
        term = "rf";
        forceDoEncode = true;
    }

	@Override
	protected void mkMaxTupleSet(){
		FilterBasic memory = FilterBasic.get(EType.MEMORY);
		FilterBasic write = FilterBasic.get(EType.WRITE);
		FilterBasic read = FilterBasic.get(EType.READ);
		boolean lc = settings.getFlag(FLAG_CURRENT_MODEL_LOCAL_RF);
		for(Thread i : program.getThreads()){

			if(lc){
				List<Event> m = i.getCache().getEvents(memory);
				for(ListIterator<Event> i1 = m.listIterator(); i1.hasNext();){
					MemEvent e1 = (MemEvent)i1.next();
					if(!e1.hasFilter(EType.WRITE))
						continue;
					for(ListIterator<Event> i2 = m.listIterator(i1.nextIndex()); i2.hasNext();){
						MemEvent e2 = (MemEvent)i2.next();
						if(e2.hasFilter(EType.READ) && MemEvent.canAddressTheSameLocation(e1,e2))
							addMaxTuple(e1,e2,false);
					}
				}
			}

			List<Event> ww = i.getCache().getEvents(write);
			if(!ww.isEmpty())
				for(Thread j : program.getThreads())
					if(!lc || !i.equals(j))
						for(Event r : j.getCache().getEvents(read))
							for(Event w : ww)
								if(MemEvent.canAddressTheSameLocation((MemEvent)w,(MemEvent)r))
									addMaxTuple(w,r,false);
            }
	}

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        Map<MemEvent, List<BoolExpr>> edgeMap = new HashMap<>();
        Map<MemEvent, BoolExpr> memInitMap = new HashMap<>();

        boolean canAccNonInitMem = settings.getFlag(Settings.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY);
        boolean useSeqEncoding = settings.getFlag(Settings.FLAG_USE_SEQ_ENCODING_REL_RF);

        for(Tuple tuple : getMaxTupleSet()){
            MemEvent w = (MemEvent) tuple.getFirst();
            MemEvent r = (MemEvent) tuple.getSecond();
            BoolExpr edge = edge(w, r);
            
            IntExpr a1 = w.getMemAddressExpr().isBV() ? ctx.mkBV2Int((BitVecExpr)w.getMemAddressExpr(), false) : (IntExpr)w.getMemAddressExpr();
            IntExpr a2 = r.getMemAddressExpr().isBV() ? ctx.mkBV2Int((BitVecExpr)r.getMemAddressExpr(), false) : (IntExpr)r.getMemAddressExpr();
            BoolExpr sameAddress = ctx.mkEq(a1, a2);
            
            IntExpr v1 = w.getMemValueExpr().isBV() ? ctx.mkBV2Int((BitVecExpr)w.getMemValueExpr(), false) : (IntExpr)w.getMemValueExpr();
            IntExpr v2 = r.getMemValueExpr().isBV() ? ctx.mkBV2Int((BitVecExpr)r.getMemValueExpr(), false) : (IntExpr)r.getMemValueExpr();
            BoolExpr sameValue = ctx.mkEq(v1, v2);

            edgeMap.putIfAbsent(r, new ArrayList<>());
            edgeMap.get(r).add(edge);
            if(canAccNonInitMem && w.is(EType.INIT)){
                memInitMap.put(r, ctx.mkOr(memInitMap.getOrDefault(r, ctx.mkFalse()), sameAddress));
            }
            enc = ctx.mkAnd(enc, ctx.mkImplies(edge, ctx.mkAnd(w.exec(), r.exec(), sameAddress, sameValue)));
        }

        for(MemEvent r : edgeMap.keySet()){
            enc = ctx.mkAnd(enc, useSeqEncoding
                    ? encodeEdgeSeq(r, memInitMap.get(r), edgeMap.get(r))
                    : encodeEdgeNaive(r, memInitMap.get(r), edgeMap.get(r)));
        }
        return enc;
    }

    private BoolExpr encodeEdgeNaive(Event read, BoolExpr isMemInit, List<BoolExpr> edges){
        BoolExpr atMostOne = ctx.mkTrue();
        BoolExpr atLeastOne = ctx.mkFalse();
        for(int i = 0; i < edges.size(); i++){
            atLeastOne = ctx.mkOr(atLeastOne, edges.get(i));
            for(int j = i + 1; j < edges.size(); j++){
                atMostOne = ctx.mkAnd(atMostOne, ctx.mkNot(ctx.mkAnd(edges.get(i), edges.get(j))));
            }
        }

        if(settings.getFlag(Settings.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY)) {
            atLeastOne = ctx.mkImplies(ctx.mkAnd(read.exec(), isMemInit), atLeastOne);
        } else {
            atLeastOne = ctx.mkImplies(read.exec(), atLeastOne);
        }
        return ctx.mkAnd(atMostOne, atLeastOne);
    }

    private BoolExpr encodeEdgeSeq(Event read, BoolExpr isMemInit, List<BoolExpr> edges){
        int num = edges.size();
        int readId = read.getCId();
        BoolExpr lastSeqVar = mkSeqVar(readId, 0);
        BoolExpr newSeqVar = lastSeqVar;
        BoolExpr atMostOne = ctx.mkEq(lastSeqVar, edges.get(0));

        for(int i = 1; i < num; i++){
            newSeqVar = mkSeqVar(readId, i);
            atMostOne = ctx.mkAnd(atMostOne, ctx.mkEq(newSeqVar, ctx.mkOr(lastSeqVar, edges.get(i))));
            atMostOne = ctx.mkAnd(atMostOne, ctx.mkNot(ctx.mkAnd(edges.get(i), lastSeqVar)));
            lastSeqVar = newSeqVar;
        }
        BoolExpr atLeastOne = ctx.mkOr(newSeqVar, edges.get(edges.size() - 1));

        if(settings.getFlag(Settings.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY)) {
            atLeastOne = ctx.mkImplies(ctx.mkAnd(read.exec(), isMemInit), atLeastOne);
        } else {
            atLeastOne = ctx.mkImplies(read.exec(), atLeastOne);
        }
        return ctx.mkAnd(atMostOne, atLeastOne);
    }

    private BoolExpr mkSeqVar(int readId, int i) {
        return (BoolExpr) ctx.mkConst("s(" + term + ",E" + readId + "," + i + ")", ctx.mkBoolSort());
    }
}
