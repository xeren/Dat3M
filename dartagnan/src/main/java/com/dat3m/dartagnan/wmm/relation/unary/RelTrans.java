package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Florian Furbach
 */
public class RelTrans extends UnaryRelation {

    Map<Event, Set<Event>> transitiveReachabilityMap;
    private TupleSet fullEncodeTupleSet;

    public static String makeTerm(Relation r1){
        return r1.getName() + "^+";
    }

    public RelTrans(Relation r1) {
        super(r1);
        term = makeTerm(r1);
    }

    public RelTrans(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

    @Override
    public void initialise(Program program, Context ctx, Settings settings){
        super.initialise(program, ctx, settings);
        fullEncodeTupleSet = new TupleSet();
        transitiveReachabilityMap = null;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            transitiveReachabilityMap = r1.getMaxTupleSet().transMap();
            maxTupleSet = new TupleSet();
            for(Event e1 : transitiveReachabilityMap.keySet()){
                for(Event e2 : transitiveReachabilityMap.get(e1)){
                    maxTupleSet.add(new Tuple(e1, e2));
                }
            }
        }
        return maxTupleSet;
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
            r1.addEncodeTupleSet(fullActiveSet);
        }
    }

    @Override
    protected BoolExpr encodeApprox(EncodeContext context) {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : fullEncodeTupleSet){
            BoolExpr orClause = ctx.mkFalse();

            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            if(r1.getMaxTupleSet().contains(tuple)){
                orClause = ctx.mkOr(orClause, r1.edge(e1, e2));
            }

            for(Event e3 : transitiveReachabilityMap.get(e1)){
                if(e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId() && transitiveReachabilityMap.get(e3).contains(e2)){
                    orClause = ctx.mkOr(orClause, ctx.mkAnd(edge(e1, e3), edge(e3, e2)));
                }
            }

            if(Relation.PostFixApprox) {
                enc = ctx.mkAnd(enc, ctx.mkImplies(orClause, edge(e1, e2)));
            } else {
                enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1, e2), orClause));
            }
        }

        return enc;
    }

    @Override
    protected BoolExpr encodeIDL(EncodeContext context) {
        BoolExpr enc = ctx.mkTrue();
        String nameConcat = "(" + getName() + ";" + getName() + ")";

        for(Tuple tuple : fullEncodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();
            BoolExpr edgeConcat = Utils.edge(nameConcat, e1, e2, ctx);
            IntExpr intCountConcat = Utils.intCount(nameConcat, e1, e2, ctx);

            BoolExpr firstCondition = ctx.mkFalse();
            for(Tuple tuple2 : fullEncodeTupleSet.getByFirst(e1)){
                Event e3 = tuple2.getSecond();
                if(!e2.equals(e3) && transitiveReachabilityMap.get(e3).contains(e2)) {
                    firstCondition = ctx.mkOr(firstCondition, ctx.mkAnd(
                        edge(e1, e3),
                        edge(e3, e2),
                        ctx.mkGt(intCountConcat, intCount(e1, e3)),
                        ctx.mkGt(intCountConcat, intCount(e3, e2))));
                }
            }

            BoolExpr secondCondition = ctx.mkFalse();
            for(Tuple tuple2 : fullEncodeTupleSet.getByFirst(e1)){
                assert !tuple2.equals(tuple);
                Event e3 = tuple2.getSecond();
                if(!e2.equals(e3) && transitiveReachabilityMap.get(e3).contains(e2)) {
                    secondCondition = ctx.mkOr(secondCondition, ctx.mkAnd(edge(e1, e3), edge(e3, e2)));
                }
            }

            enc = ctx.mkAnd(enc,
                    ctx.mkEq(edgeConcat, firstCondition),
                    ctx.mkEq(edgeConcat, secondCondition),
                    ctx.mkImplies(edgeConcat, ctx.mkGt(intCount(e1, e2), intCountConcat)),
                    ctx.mkEq(edge(e1, e2), ctx.mkOr(r1.edge(e1, e2), edgeConcat)));
        }

        return enc;
    }

    @Override
    protected BoolExpr encodeLFP(EncodeContext context) {
        BoolExpr enc = ctx.mkTrue();
        int iteration = 0;

        // Encode initial iteration
        Set<Tuple> currentTupleSet = new HashSet<>(r1.getEncodeTupleSet());
        for(Tuple tuple : currentTupleSet){
            enc = ctx.mkAnd(enc, ctx.mkEq(r1.edge(iteration, tuple), r1.edge(tuple)));
        }

        while(true){
            Map<Tuple, Set<BoolExpr>> currentTupleMap = new HashMap<>();
            Set<Tuple> newTupleSet = new HashSet<>();

            // Original tuples from the previous iteration
            for(Tuple tuple : currentTupleSet){
                currentTupleMap.putIfAbsent(tuple, new HashSet<>());
                currentTupleMap.get(tuple).add(r1.edge(iteration, tuple));
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
                                r1.edge(iteration, e1, e3),
                                r1.edge(iteration, e3, e2)
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

                BoolExpr edge = r1.edge(iteration, tuple);
                enc = ctx.mkAnd(enc, ctx.mkEq(edge, orClause));
            }

            if(!currentTupleSet.addAll(newTupleSet)){
                break;
            }
        }

        // Encode that transitive relation equals the relation at the last iteration
        for(Tuple tuple : encodeTupleSet){
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), r1.edge(iteration, tuple)));
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
                for (Event e3 : transitiveReachabilityMap.get(e1)) {
                    if (e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId()
                            && transitiveReachabilityMap.get(e3).contains(e2)) {
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

    protected BoolExpr encodeFirstOrder(EncodeContext context) {
        return ctx.mkAnd(
            forall(0, (a,c)->ctx.mkOr(ctx.mkNot(edge(a, c)), r1.edge(a, c),
                    exists(2, b->ctx.mkAnd(edge(a, b), edge(b, c)),
                        b->ctx.mkPattern(edge(a, b), edge(b, c)))),
                (a,c)->ctx.mkPattern(edge(a, c))),
            forall(0, (a,c)->ctx.mkImplies(r1.edge(a, c), edge(a, c)),
                (a,c)->ctx.mkPattern(r1.edge(a, c))),
            forall(0, (a,b,c)->ctx.mkImplies(ctx.mkAnd(edge(a, b), edge(b, c)), edge(a, c)),
                (a,b,c)->ctx.mkPattern(edge(a, b), edge(a, c))));
    }
}