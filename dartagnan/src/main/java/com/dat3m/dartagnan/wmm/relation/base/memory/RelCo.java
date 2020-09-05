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
import java.util.LinkedList;
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
    protected BoolExpr encodeApprox(EncodeContext e) {
        String name = getName();
        LinkedList<BoolExpr> enc = new LinkedList<>();

        List<Event> eventsInit = program.getCache().getEvents(FilterBasic.get(EType.INIT));
        List<Event> eventsStore = program.getCache().getEvents(FilterMinus.get(
                FilterBasic.get(EType.WRITE),
                FilterBasic.get(EType.INIT)
        ));

        for(Event i : eventsInit) {
            enc.add(e.eq(e.intVar(name, i), e.zero()));
        }

        List<IntExpr> intVars = new ArrayList<>();
        for(Event w : eventsStore) {
            IntExpr coVar = e.intVar(name, w);
            enc.add(e.lt(e.zero(), coVar));
            intVars.add(coVar);
        }
        enc.add(e.distinct(intVars));

        for(Event w : program.getCache().getEvents(FilterBasic.get(EType.WRITE))){
            MemEvent w1 = (MemEvent)w;
            LinkedList<BoolExpr> lastCo = new LinkedList<>();
            lastCo.add(w1.exec());

            for(Tuple t : maxTupleSet.getByFirst(w1)){
                MemEvent w2 = (MemEvent)t.getSecond();
                BoolExpr relation = e.edge(this, w1, w2);
                lastCo.add(e.not(relation));

                enc.add(e.eq(relation, e.and(
                    w1.exec(),
                    w2.exec(),
                    e.eq(w1.getMemAddressExpr(), w2.getMemAddressExpr()),
                    e.lt(e.intVar(name, w1), e.intVar(name, w2))
                )));
            }

            BoolExpr lastCoExpr = e.context.mkBoolConst("co_last(" + w1.repr() + ")");
            enc.add(e.eq(lastCoExpr, e.and(lastCo)));

            for(Address address : w1.getMaxAddressSet()){
                enc.add(e.implies(
                        e.and(lastCoExpr, e.eq(w1.getMemAddressExpr(), address.toZ3Int(e.context))),
                        e.eq(address.getLastMemValueExpr(e.context), w1.getMemValueExpr())
                ));
            }
        }
        return e.and(enc);
    }

    @Override
    protected BoolExpr encodeFirstOrder(EncodeContext e) {
        List<Event> eventsInit = program.getCache().getEvents(FilterBasic.get(EType.INIT));
        List<Event> eventsWrite = program.getCache().getEvents(FilterBasic.get(EType.WRITE));
        List<Event> eventsStore = program.getCache().getEvents(
                FilterMinus.get(FilterBasic.get(EType.WRITE), FilterBasic.get(EType.INIT)));
        EncodeContext.RelationPredicate edge = e.of(this);

        BoolExpr typed = e.forall(0, (a,b)->e.eq(edge.of(a, b),
                e.and(
                    e.not(edge.of(b, a)),
                    e.or(eventsWrite.stream().map(MemEvent.class::cast).map(v->e.and(
                        v.exec(),
                        e.eq(a, e.event(v)),
                        e.or(eventsStore.stream()
                            // already implied by asymmetric, but shortens the formula
                            .filter(w->v.getCId() != w.getCId())
                            .map(MemEvent.class::cast)
                            .map(w->e.and(
                                w.exec(),
                                e.eq(b, e.event(w)),
                                // pair has same address
                                e.eq(v.getMemAddressExpr(), w.getMemAddressExpr()))))))))),
            (a,b)->e.pattern(edge.of(a, b)));

        BoolExpr transitive = e.forall(0, (a,b,c)->e.implies(edge.of(a, b), e.implies(edge.of(b, c), edge.of(a, c))),
                (a,b,c)->e.pattern(edge.of(a, b), edge.of(b, c)));

        BoolExpr initial = e.and(eventsInit.stream().map(MemEvent.class::cast)
                .flatMap(v->eventsStore.stream().map(MemEvent.class::cast)
                    .map(w->e.not(edge.of(e.event(v), e.event(w))))));

        return e.and(typed, transitive, initial);
    }
}
