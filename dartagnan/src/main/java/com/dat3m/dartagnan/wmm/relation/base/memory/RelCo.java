package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterMinus;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.ArrayList;
import java.util.List;

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
    protected BoolExpr encodeApprox(EncodeContext context) {
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

            for(Tuple t : maxTupleSet.getByFirst(w1)){
                MemEvent w2 = (MemEvent)t.getSecond();
                BoolExpr relation = edge(w1, w2);
                lastCo = ctx.mkAnd(lastCo, ctx.mkNot(edge(w1, w2)));

                enc = ctx.mkAnd(enc, ctx.mkEq(relation, ctx.mkAnd(
                        ctx.mkAnd(ctx.mkAnd(w1.exec(), w2.exec()), ctx.mkEq(w1.getMemAddressExpr(), w2.getMemAddressExpr())),
                        ctx.mkLt(intVar(w1), intVar(w2))
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
    protected BoolExpr encodeFirstOrder(EncodeContext c) {
        List<Event> eventsInit = program.getCache().getEvents(FilterBasic.get(EType.INIT));
        List<Event> eventsWrite = program.getCache().getEvents(FilterBasic.get(EType.WRITE));
        List<Event> eventsStore = program.getCache().getEvents(
                FilterMinus.get(FilterBasic.get(EType.WRITE), FilterBasic.get(EType.INIT)));
        EncodeContext.RelationPredicate edge = c.of(this);

        BoolExpr typed = c.forall(0, (a,b)->c.eq(edge.of(a, b),
                c.and(
                    c.not(edge.of(b, a)),
                    c.or(eventsWrite.stream().map(MemEvent.class::cast).map(v->c.and(
                        v.exec(),
                        c.eq(a, c.event(v)),
                        c.or(eventsStore.stream()
                            // already implied by asymmetric, but shortens the formula
                            .filter(w->v.getCId() != w.getCId())
                            .map(MemEvent.class::cast)
                            .map(w->c.and(
                                w.exec(),
                                c.eq(b, c.event(w)),
                                // pair has same address
                                c.eq(v.getMemAddressExpr(), w.getMemAddressExpr()))))))))),
            (a,b)->c.pattern(edge.of(a, b)));

        BoolExpr transitive = c.forall(0, (a,b,d)->c.implies(edge.of(a, b), c.implies(edge.of(b, d), edge.of(a, d))),
                (a,b,d)->c.pattern(edge.of(a, b), edge.of(b, d)));

        BoolExpr initial = c.and(eventsInit.stream().map(MemEvent.class::cast)
                .flatMap(v->eventsStore.stream().map(MemEvent.class::cast)
                    .map(w->c.not(edge.of(c.event(v), c.event(w))))));

        return c.and(typed, transitive, initial);
    }
}
