package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.Computation;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterMinus;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.ArrayList;
import java.util.List;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;
import static com.dat3m.dartagnan.wmm.utils.Utils.intVar;

public class RelCo extends Relation {

    public RelCo(){
        term = "co";
        forceDoEncode = true;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            List<Event> eventsInit = program.getCache().getEvents(FilterBasic.get(EType.INIT));
            List<Event> eventsStore = program.getCache().getEvents(FilterMinus.get(
                    FilterBasic.get(EType.WRITE),
                    FilterBasic.get(EType.INIT)
            ));

            for(Event e1 : eventsInit){
                for(Event e2 : eventsStore){
                    if(MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent)e2)){
                        maxTupleSet.add(new Tuple(e1, e2));
                    }
                }
            }

            for(Event e1 : eventsStore){
                for(Event e2 : eventsStore){
                    if(e1.getCId() != e2.getCId() && MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent)e2)){
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

        List<Event> eventsInit = program.getCache().getEvents(FilterBasic.get(EType.INIT));
        List<Event> eventsStore = program.getCache().getEvents(FilterMinus.get(
                FilterBasic.get(EType.WRITE),
                FilterBasic.get(EType.INIT)
        ));

        for(Event e : eventsInit) {
            enc = ctx.mkAnd(enc, ctx.mkEq(intVar("co", e, ctx), ctx.mkInt(0)));
        }

        List<IntExpr> intVars = new ArrayList<>();
        for(Event w : eventsStore) {
            IntExpr coVar = intVar("co", w, ctx);
            enc = ctx.mkAnd(enc, ctx.mkGt(coVar, ctx.mkInt(0)));
            intVars.add(coVar);
        }
        enc = ctx.mkAnd(enc, ctx.mkDistinct(intVars.toArray(new IntExpr[0])));

        for(Event w :  program.getCache().getEvents(FilterBasic.get(EType.WRITE))){
            MemEvent w1 = (MemEvent)w;
            BoolExpr lastCo = w1.exec();

            for(Tuple t : maxTupleSet.getByFirst(w1)){
                MemEvent w2 = (MemEvent)t.getSecond();
                BoolExpr relation = edge("co", w1, w2, ctx);
                lastCo = ctx.mkAnd(lastCo, ctx.mkNot(edge("co", w1, w2, ctx)));

                enc = ctx.mkAnd(enc, ctx.mkEq(relation, ctx.mkAnd(
                        ctx.mkAnd(ctx.mkAnd(w1.exec(), w2.exec()), ctx.mkEq(w1.getMemAddressExpr(), w2.getMemAddressExpr())),
                        ctx.mkLt(Utils.intVar("co", w1, ctx), Utils.intVar("co", w2, ctx))
                )));
            }

            BoolExpr lastCoExpr = ctx.mkBoolConst("co_last(" + w1.repr() + ")");
            enc = ctx.mkAnd(enc, ctx.mkEq(lastCoExpr, lastCo));

            for(Address address : w1.getMaxAddressSet()){
                enc = ctx.mkAnd(enc, ctx.mkImplies(
                        ctx.mkAnd(lastCoExpr, ctx.mkEq(w1.getMemAddressExpr(), address.toZ3Int(ctx))),
                        ctx.mkEq(address.getLastMemValueExpr(ctx), w1.getMemValueExpr())
                ));
            }
        }
        return enc;
    }

    @Override
    public Computation.Relation register(Computation computation) {
        if(computation.relation.containsKey(this))
            return computation.relation.get(this);
        Computation.Relation r = new Computation.Relation();
        computation.relation.put(this, r);
        computation.forEachLocation((x,y)->{
            if(!(y instanceof com.dat3m.dartagnan.wmm.Event.Init))
                r.addMax(x,y);
            if(!(x instanceof com.dat3m.dartagnan.wmm.Event.Init))
                r.addMax(y,x);});
        return r;
    }
}
