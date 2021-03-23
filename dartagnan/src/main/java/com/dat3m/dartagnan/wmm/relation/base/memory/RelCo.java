package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterMinus;
import com.microsoft.z3.*;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;

import java.util.ArrayList;
import java.util.List;

public class RelCo extends Relation {

    public RelCo(){
        term = "co";
        forceDoEncode = true;
    }

    public IntExpr intVar(Event event) {
        return ctx.mkIntConst("co "+event.getCId());
    }

	@Override
	protected void mkMaxTupleSet(){
            List<Event> eventsInit = program.getCache().getEvents(FilterBasic.get(EType.INIT));
            List<Event> eventsStore = program.getCache().getEvents(FilterMinus.get(
                    FilterBasic.get(EType.WRITE),
                    FilterBasic.get(EType.INIT)
            ));

            for(Event e1 : eventsInit){
                for(Event e2 : eventsStore){
                    if(MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent)e2)){
					addMaxTuple(e1,e2);
                    }
                }
            }

            for(Event e1 : eventsStore){
                for(Event e2 : eventsStore){
                    if(e1.getCId() != e2.getCId() && MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent)e2)){
					addMaxTuple(e1,e2);
                    }
                }
            }
	}

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

        List<Event> eventsInit = program.getCache().getEvents(FilterBasic.get(EType.INIT));
        List<Event> eventsStore = program.getCache().getEvents(FilterMinus.get(
                FilterBasic.get(EType.WRITE),
                FilterBasic.get(EType.INIT)
        ));

        for(Event e : eventsInit) {
            enc = ctx.mkAnd(enc, ctx.mkEq(intVar(e), ctx.mkInt(0)));
        }

        List<IntExpr> intVars = new ArrayList<>();
        for(Event w : eventsStore) {
            IntExpr coVar = intVar(w);
            enc = ctx.mkAnd(enc, ctx.mkGt(coVar, ctx.mkInt(0)));
            intVars.add(coVar);
        }
        enc = ctx.mkAnd(enc, ctx.mkDistinct(intVars.toArray(new IntExpr[0])));

        for(Event w :  program.getCache().getEvents(FilterBasic.get(EType.WRITE))){
            MemEvent w1 = (MemEvent)w;
            BoolExpr lastCo = w1.exec();

            for(Tuple t : getMaxTupleSet(w1)){
                MemEvent w2 = (MemEvent)t.getSecond();
                BoolExpr relation = edge(w1, w2);
                lastCo = ctx.mkAnd(lastCo, ctx.mkNot(edge(w1, w2)));

                Expr a1 = w1.getMemAddressExpr().isBV() ? ctx.mkBV2Int((BitVecExpr)w1.getMemAddressExpr(), false) : w1.getMemAddressExpr();
                Expr a2 = w2.getMemAddressExpr().isBV() ? ctx.mkBV2Int((BitVecExpr)w2.getMemAddressExpr(), false) : w2.getMemAddressExpr();
                enc = ctx.mkAnd(enc, ctx.mkEq(relation, ctx.mkAnd(
                        ctx.mkAnd(ctx.mkAnd(w1.exec(), w2.exec()), ctx.mkEq(a1, a2)),
                        ctx.mkLt(intVar(w1), intVar(w2))
                )));
            }

            BoolExpr lastCoExpr = ctx.mkBoolConst("co_last(" + w1.repr() + ")");
            enc = ctx.mkAnd(enc, ctx.mkEq(lastCoExpr, lastCo));

            for(Address address : w1.getMaxAddressSet()){
            	Expr a1 = w1.getMemAddressExpr().isBV() ? ctx.mkBV2Int((BitVecExpr)w1.getMemAddressExpr(), false) : w1.getMemAddressExpr();
            	Expr a2 = address.toZ3Int(ctx).isBV() ? ctx.mkBV2Int((BitVecExpr)address.toZ3Int(ctx), false) : address.toZ3Int(ctx);
				Expr v1 = address.getLastMemValueExpr(ctx).isBV() ? ctx.mkBV2Int((BitVecExpr)address.getLastMemValueExpr(ctx), false) : address.getLastMemValueExpr(ctx);
                Expr v2 = w1.getMemValueExpr().isBV() ? ctx.mkBV2Int((BitVecExpr)w1.getMemValueExpr(), false) : w1.getMemValueExpr();
				enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.mkAnd(lastCoExpr, ctx.mkEq(a1, a2)),ctx.mkEq(v1, v2)));
            }
        }
        return enc;
    }
}
