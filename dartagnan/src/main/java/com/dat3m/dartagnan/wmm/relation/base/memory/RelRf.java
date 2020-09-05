package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterMinus;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.*;

public class RelRf extends Relation {

    public RelRf(){
        term = "rf";
        forceDoEncode = true;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();

            List<Event> eventsLoad = program.getCache().getEvents(FilterBasic.get(EType.READ));
            List<Event> eventsInit = program.getCache().getEvents(FilterBasic.get(EType.INIT));
            List<Event> eventsStore = program.getCache().getEvents(FilterMinus.get(
                    FilterBasic.get(EType.WRITE),
                    FilterBasic.get(EType.INIT)
            ));

            for(Event e1 : eventsInit){
                for(Event e2 : eventsLoad){
                    if(MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent) e2)){
                        maxTupleSet.add(new Tuple(e1, e2));
                    }
                }
            }

            for(Event e1 : eventsStore){
                for(Event e2 : eventsLoad){
                    if(MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent) e2)){
                    	maxTupleSet.add(new Tuple(e1, e2));
                    }
                }
            }
        }
        return maxTupleSet;
    }

    @Override
    protected BoolExpr encodeApprox(EncodeContext context) {
        BoolExpr enc = ctx.mkTrue();
        Map<MemEvent, List<BoolExpr>> edgeMap = new HashMap<>();
        Map<MemEvent, BoolExpr> memInitMap = new HashMap<>();

        boolean canAccNonInitMem = context.settings.getFlag(Settings.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY);

        for(Tuple tuple : maxTupleSet){
            MemEvent w = (MemEvent) tuple.getFirst();
            MemEvent r = (MemEvent) tuple.getSecond();
            BoolExpr edge = edge(w, r);
            BoolExpr sameAddress = ctx.mkEq(w.getMemAddressExpr(), r.getMemAddressExpr());
            BoolExpr sameValue = ctx.mkEq(w.getMemValueExpr(), r.getMemValueExpr());

            edgeMap.putIfAbsent(r, new ArrayList<>());
            edgeMap.get(r).add(edge);
            if(canAccNonInitMem && w.is(EType.INIT)){
                memInitMap.put(r, ctx.mkOr(memInitMap.getOrDefault(r, ctx.mkFalse()), sameAddress));
            }
            enc = ctx.mkAnd(enc, ctx.mkImplies(edge, ctx.mkAnd(w.exec(), r.exec(), sameAddress, sameValue)));
        }

        if(context.settings.getFlag(Settings.FLAG_USE_SEQ_ENCODING_REL_RF)) {
            for(MemEvent r : edgeMap.keySet()){
                List<BoolExpr> edges = edgeMap.get(r);
                int num = edges.size();
                int readId = r.getCId();
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
                enc = ctx.mkAnd(enc, atMostOne,
                    ctx.mkImplies(canAccNonInitMem ? ctx.mkAnd(r.exec(), memInitMap.get(r)) : r.exec(), atLeastOne));
            }
        } else {
            for(MemEvent r : edgeMap.keySet()){
                List<BoolExpr> edges = edgeMap.get(r);
                BoolExpr atMostOne = ctx.mkTrue();
                BoolExpr atLeastOne = ctx.mkFalse();
                for(int i = 0; i < edges.size(); i++){
                    atLeastOne = ctx.mkOr(atLeastOne, edges.get(i));
                    for(int j = i + 1; j < edges.size(); j++){
                        atMostOne = ctx.mkAnd(atMostOne, ctx.mkNot(ctx.mkAnd(edges.get(i), edges.get(j))));
                    }
                }
                enc = ctx.mkAnd(enc, atMostOne,
                    ctx.mkImplies(canAccNonInitMem ? ctx.mkAnd(r.exec(), memInitMap.get(r)) : r.exec(), atLeastOne));
            }
        }
        return enc;
    }

    private BoolExpr mkSeqVar(int readId, int i) {
        return (BoolExpr) ctx.mkConst("s(" + term + ",E" + readId + "," + i + ")", ctx.mkBoolSort());
    }

    @Override
    protected BoolExpr encodeFirstOrder(EncodeContext context) {
        BoolExpr max = forall(0, (a,b)->ctx.mkImplies(edge(a,b), or(maxTupleSet.stream().map(t->ctx.mkAnd(
                t.getFirst().exec(),
                t.getSecond().exec(),
                ctx.mkEq(a, context.event(t.getFirst())),
                ctx.mkEq(b, context.event(t.getSecond())),
                ctx.mkEq(((MemEvent)t.getFirst()).getMemAddressExpr(), ((MemEvent)t.getSecond()).getMemAddressExpr()),
                ctx.mkEq(((MemEvent)t.getFirst()).getMemValueExpr(), ((MemEvent)t.getSecond()).getMemValueExpr()))))),
                (a,b)->ctx.mkPattern(edge(a,b)));
        BoolExpr satisfaction = and(program.getCache().getEvents(FilterBasic.get(EType.READ)).stream()
                .map(r->exists(0, w->edge(w, context.event(r)))));
        BoolExpr determinism = forall(0, (a,b,c)->ctx.mkImplies(ctx.mkAnd(edge(a,c), edge(b,c)), ctx.mkEq(a, b)),
                (a,b,c)->ctx.mkPattern(edge(a, c), edge(b, c)));
        return ctx.mkAnd(max, satisfaction, determinism);
    }
}
