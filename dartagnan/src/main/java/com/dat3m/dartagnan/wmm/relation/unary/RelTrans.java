package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;
import static com.dat3m.dartagnan.wmm.utils.Utils.intCount;

/**
 *
 * @author Florian Furbach
 */
public class RelTrans extends UnaryRelation {

    private TupleSet fullEncodeTupleSet;
    protected final boolean isClosedReflexively;

    public static String makeTerm(Relation r1){
        return r1.getName() + "^+";
    }

    protected RelTrans(boolean ref, Relation r1) {
        super(r1);
        isClosedReflexively = ref;
    }

    protected RelTrans(boolean ref, Relation r1, String name) {
        super(r1,name);
        isClosedReflexively = ref;
    }

    public RelTrans(Relation r1) {
        this(false,r1);
        term = makeTerm(r1);
    }

    public RelTrans(Relation r1, String name) {
        this(false,r1,name);
        term = makeTerm(r1);
    }

    @Override
    public void initialise(Program program, Context ctx, Settings settings){
        super.initialise(program, ctx, settings);
        fullEncodeTupleSet = new TupleSet();
    }

    @Override
    protected void compute(TupleSet s, TupleSet s1) {
        for(Map.Entry<Event,Set<Event>> e : s1.transMap().entrySet())
            for(Event e2 : e.getValue())
                s.add(new Tuple(e.getKey(),e2));
        if(isClosedReflexively)
            for(Event e : program.getCache().getEvents(FilterBasic.get(EType.ANY)))
                s.add(new Tuple(e,e));
    }

    @Override
    public void addEncodeTupleSet(TupleSet tuples){
        TupleSet activeSet = new TupleSet();
        activeSet.addAll(tuples);
        activeSet.removeAll(encodeTupleSet);
        encodeTupleSet.addAll(activeSet);
        activeSet.retainAll(maxTupleSet);

        TupleSet fullActiveSet = getFullEncodeTupleSet(activeSet);
        if(fullEncodeTupleSet.addAll(fullActiveSet)){
            fullActiveSet.retainAll(r1.getMaxTupleSet());
            if(isClosedReflexively)
                fullActiveSet.removeIf(t->t.getFirst().getCId()==t.getSecond().getCId());
            r1.addEncodeTupleSet(fullActiveSet);
        }
    }

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : fullEncodeTupleSet){
            BoolExpr orClause = ctx.mkFalse();

            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            if(isClosedReflexively && e1.getCId() == e2.getCId()) {
                enc = ctx.mkAnd(Utils.edge(getName(),e1,e2,ctx));
                continue;
            }

            if(r1.getMaxTupleSet().contains(new Tuple(e1, e2))){
                orClause = ctx.mkOr(orClause, Utils.edge(r1.getName(), e1, e2, ctx));
            }

            for(Tuple t : maxTupleSet.getByFirst(e1)){
                Event e3 = t.getSecond();
                if(e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId() && maxTupleSet.contains(new Tuple(e3,e2))){
                    orClause = ctx.mkOr(orClause, ctx.mkAnd(Utils.edge(this.getName(), e1, e3, ctx), Utils.edge(this.getName(), e3, e2, ctx)));
                }
            }

            if(Relation.PostFixApprox) {
                enc = ctx.mkAnd(enc, ctx.mkImplies(orClause, Utils.edge(this.getName(), e1, e2, ctx)));
            } else {
                enc = ctx.mkAnd(enc, ctx.mkEq(Utils.edge(this.getName(), e1, e2, ctx), orClause));
            }
        }

        return enc;
    }

    @Override
    protected BoolExpr encodeIDL() {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : fullEncodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            if(isClosedReflexively && e1.getCId() == e2.getCId()) {
                enc = ctx.mkAnd(Utils.edge(getName(),e1,e2,ctx));
                continue;
            }

            BoolExpr orClause = ctx.mkFalse();
            for(Tuple tuple2 : fullEncodeTupleSet.getByFirst(e1)){
                Event e3 = tuple2.getSecond();
                if (e1.getCId() != e3.getCId() && e3.getCId() != e2.getCId() && maxTupleSet.contains(new Tuple(e3,e2))) {
                    orClause = ctx.mkOr(orClause, ctx.mkAnd(
                            edge(this.getName(), e1, e3, ctx),
                            edge(this.getName(), e3, e2, ctx),
                            ctx.mkGt(intCount(this.idlConcatName(), e1, e2, ctx), intCount(this.getName(), e1, e3, ctx)),
                            ctx.mkGt(intCount(this.idlConcatName(), e1, e2, ctx), intCount(this.getName(), e3, e2, ctx))));
                }
            }

            enc = ctx.mkAnd(enc, ctx.mkEq(edge(this.idlConcatName(), e1, e2, ctx), orClause));

            orClause = ctx.mkFalse();
            for(Tuple tuple2 : fullEncodeTupleSet.getByFirst(e1)){
                Event e3 = tuple2.getSecond();
                if (e1.getCId() != e3.getCId() && e3.getCId() != e2.getCId() && maxTupleSet.contains(new Tuple(e3,e2))) {
                    orClause = ctx.mkOr(orClause, ctx.mkAnd(
                            edge(this.getName(), e1, e3, ctx),
                            edge(this.getName(), e3, e2, ctx)));
                }
            }

            enc = ctx.mkAnd(enc, ctx.mkEq(edge(this.idlConcatName(), e1, e2, ctx), orClause));

            enc = ctx.mkAnd(enc, ctx.mkEq(edge(this.getName(), e1, e2, ctx), ctx.mkOr(
                    edge(r1.getName(), e1,e2, ctx),
                    ctx.mkAnd(edge(this.idlConcatName(), e1, e2, ctx), ctx.mkGt(intCount(this.getName(), e1, e2, ctx), intCount(this.idlConcatName(), e1, e2, ctx)))
            )));

            enc = ctx.mkAnd(enc, ctx.mkEq(edge(this.getName(),e1,e2, ctx), ctx.mkOr(
                    edge(r1.getName(), e1,e2, ctx),
                    edge(this.idlConcatName(), e1, e2, ctx)
            )));
        }

        return enc;
    }

    @Override
    protected BoolExpr encodeLFP() {
        BoolExpr enc = ctx.mkTrue();
        int iteration = 0;

        // Encode initial iteration
        Set<Tuple> currentTupleSet = new HashSet<>(r1.getEncodeTupleSet());
        for(Tuple tuple : currentTupleSet){
            enc = ctx.mkAnd(enc, ctx.mkEq(
                    Utils.edge(r1.getName() + "_" + iteration, tuple.getFirst(), tuple.getSecond(), ctx),
                    Utils.edge(r1.getName(), tuple.getFirst(), tuple.getSecond(), ctx)
            ));
        }

        while(true){
            Map<Tuple, Set<BoolExpr>> currentTupleMap = new HashMap<>();
            Set<Tuple> newTupleSet = new HashSet<>();

            // Original tuples from the previous iteration
            for(Tuple tuple : currentTupleSet){
                currentTupleMap.putIfAbsent(tuple, new HashSet<>());
                currentTupleMap.get(tuple).add(
                        Utils.edge(r1.getName() + "_" + iteration, tuple.getFirst(), tuple.getSecond(), ctx)
                );
            }

            // Combine tuples from the previous iteration
            for(Tuple tuple1 : currentTupleSet){
                Event e1 = tuple1.getFirst();
                Event e3 = tuple1.getSecond();
                for(Tuple tuple2 : currentTupleSet){
                    if(e3.getCId() == tuple2.getFirst().getCId()){
                        Event e2 = tuple2.getSecond();
                        Tuple newTuple = new Tuple(e1, e2);
                        currentTupleMap.putIfAbsent(newTuple, new HashSet<>());
                        currentTupleMap.get(newTuple).add(ctx.mkAnd(
                                Utils.edge(r1.getName() + "_" + iteration, e1, e3, ctx),
                                Utils.edge(r1.getName() + "_" + iteration, e3, e2, ctx)
                        ));

                        if(newTuple.getFirst().getCId() != newTuple.getSecond().getCId()){
                            newTupleSet.add(newTuple);
                        }
                    }
                }
            }

            iteration++;

            // Encode this iteration
            for(Tuple tuple : currentTupleMap.keySet()){
                BoolExpr orClause = ctx.mkFalse();
                for(BoolExpr expr : currentTupleMap.get(tuple)){
                    orClause = ctx.mkOr(orClause, expr);
                }

                BoolExpr edge = Utils.edge(r1.getName() + "_" + iteration, tuple.getFirst(), tuple.getSecond(), ctx);
                enc = ctx.mkAnd(enc, ctx.mkEq(edge, orClause));
            }

            if(!currentTupleSet.addAll(newTupleSet)){
                break;
            }
        }

        // Encode that transitive relation equals the relation at the last iteration
        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            if(isClosedReflexively && e1.getCId() == e2.getCId()) {
                enc = ctx.mkAnd(Utils.edge(getName(),e1,e2,ctx));
                continue;
            }

            enc = ctx.mkAnd(enc, ctx.mkEq(
                    Utils.edge(getName(), e1, e2, ctx),
                    Utils.edge(r1.getName() + "_" + iteration, e1, e2, ctx)
            ));
        }

        return enc;
    }

    private TupleSet getFullEncodeTupleSet(TupleSet tuples){
        TupleSet processNow = new TupleSet();
        processNow.addAll(tuples);
        processNow.retainAll(getMaxTupleSet());

        TupleSet result = new TupleSet();

        while(!processNow.isEmpty()) {
            TupleSet processNext = new TupleSet();
            result.addAll(processNow);

            for (Tuple tuple : processNow) {
                Event e1 = tuple.getFirst();
                Event e2 = tuple.getSecond();
                for (Tuple t : maxTupleSet.getByFirst(e1)) {
                    Event e3 = t.getSecond();
                    if (e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId() && maxTupleSet.contains(new Tuple(e3,e2))) {
                        processNext.add(new Tuple(e1, e3));
                        processNext.add(new Tuple(e3, e2));
                    }
                }
            }
            processNext.removeAll(result);
            processNow = processNext;
        }
        return result;
    }

    private String idlConcatName(){
        return "(" + getName() + ";" + getName() + ")";
    }
}