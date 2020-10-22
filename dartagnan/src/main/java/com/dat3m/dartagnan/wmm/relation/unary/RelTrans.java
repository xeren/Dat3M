package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.utils.Encoder;
import com.dat3m.dartagnan.utils.EncoderFO;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.program.Program;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.Expr;

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
    public void initialise(Program program, Encoder ctx, Settings settings){
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
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : fullEncodeTupleSet){
            BoolExpr orClause = ctx.mkFalse();

            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            if(r1.getMaxTupleSet().contains(new Tuple(e1, e2))){
                orClause = ctx.mkOr(orClause, ctx.edge(r1.getName(), e1, e2));
            }

            for(Event e3 : transitiveReachabilityMap.get(e1)){
                if(e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId() && transitiveReachabilityMap.get(e3).contains(e2)){
                    orClause = ctx.mkOr(orClause, ctx.mkAnd(ctx.edge(getName(), e1, e3), ctx.edge(getName(), e3, e2)));
                }
            }

            if(Relation.PostFixApprox) {
                enc = ctx.mkAnd(enc, ctx.mkImplies(orClause, ctx.edge(getName(), e1, e2)));
            } else {
                enc = ctx.mkAnd(enc, ctx.mkEq(ctx.edge(getName(), e1, e2), orClause));
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

            BoolExpr orClause = ctx.mkFalse();
            for(Tuple tuple2 : fullEncodeTupleSet.getByFirst(e1)){
                if (!tuple2.equals(tuple)) {
                    Event e3 = tuple2.getSecond();
                    if (transitiveReachabilityMap.get(e3).contains(e2)) {
                        orClause = ctx.mkOr(orClause, ctx.mkAnd(
                                ctx.edge(getName(), e1, e3),
                                ctx.edge(getName(), e3, e2),
                                ctx.mkGt(ctx.intCount(idlConcatName(), e1, e2), ctx.intCount(getName(), e1, e3)),
                                ctx.mkGt(ctx.intCount(idlConcatName(), e1, e2), ctx.intCount(getName(), e3, e2))));
                    }
                }
            }

            enc = ctx.mkAnd(enc, ctx.mkEq(ctx.edge(idlConcatName(), e1, e2), orClause));

            orClause = ctx.mkFalse();
            for(Tuple tuple2 : fullEncodeTupleSet.getByFirst(e1)){
                if (!tuple2.equals(tuple)) {
                    Event e3 = tuple2.getSecond();
                    if (transitiveReachabilityMap.get(e3).contains(e2)) {
                        orClause = ctx.mkOr(orClause, ctx.mkAnd(
                                ctx.edge(getName(), e1, e3),
                                ctx.edge(getName(), e3, e2)));
                    }
                }
            }

            enc = ctx.mkAnd(enc, ctx.mkEq(ctx.edge(idlConcatName(), e1, e2), orClause));

            enc = ctx.mkAnd(enc, ctx.mkEq(ctx.edge(getName(), e1, e2), ctx.mkOr(
                    ctx.edge(r1.getName(), e1, e2),
                    ctx.mkAnd(ctx.edge(idlConcatName(), e1, e2), ctx.mkGt(ctx.intCount(getName(), e1, e2), ctx.intCount(idlConcatName(), e1, e2)))
            )));

            enc = ctx.mkAnd(enc, ctx.mkEq(ctx.edge(getName(), e1, e2), ctx.mkOr(
                    ctx.edge(r1.getName(), e1, e2),
                    ctx.edge(idlConcatName(), e1, e2)
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
                    ctx.edge(r1.getName() + "_" + iteration, tuple.getFirst(), tuple.getSecond()),
                    ctx.edge(r1.getName(), tuple.getFirst(), tuple.getSecond())
            ));
        }

        while(true){
            Map<Tuple, Set<BoolExpr>> currentTupleMap = new HashMap<>();
            Set<Tuple> newTupleSet = new HashSet<>();

            // Original tuples from the previous iteration
            for(Tuple tuple : currentTupleSet){
                currentTupleMap.putIfAbsent(tuple, new HashSet<>());
                currentTupleMap.get(tuple).add(
                        ctx.edge(r1.getName() + "_" + iteration, tuple.getFirst(), tuple.getSecond())
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
                                ctx.edge(r1.getName() + "_" + iteration, e1, e3),
                                ctx.edge(r1.getName() + "_" + iteration, e3, e2)
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

                BoolExpr edge = ctx.edge(r1.getName() + "_" + iteration, tuple.getFirst(), tuple.getSecond());
                enc = ctx.mkAnd(enc, ctx.mkEq(edge, orClause));
            }

            if(!currentTupleSet.addAll(newTupleSet)){
                break;
            }
        }

        // Encode that transitive relation equals the relation at the last iteration
        for(Tuple tuple : encodeTupleSet){
            enc = ctx.mkAnd(enc, ctx.mkEq(
                    ctx.edge(getName(), tuple.getFirst(), tuple.getSecond()),
                    ctx.edge(r1.getName() + "_" + iteration, tuple.getFirst(), tuple.getSecond())
            ));
        }

        return enc;
    }

    protected BoolExpr encodeFO() {
        EncoderFO c = (EncoderFO)ctx;
        Expr[] e = new Expr[]{c.bind(0), c.bind(1), c.bind(2)};
        BoolExpr e1 = c.edge(r1.getName()).of(e[0], e[1]);
        BoolExpr e2 = c.edge(getName()).of(e[0], e[1]);
        BoolExpr e3 = c.edge(getName()).of(e[1], e[2]);
        return c.mkAnd(
            c.forall(new Expr[]{e[0], e[1]}, c.mkImplies(e1, e2), c.pattern(e1)),
            c.forall(e, c.mkImplies(c.mkAnd(e2, e3), c.edge(getName()).of(e[0], e[2])), c.pattern(e2, e3)));
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

    private String idlConcatName(){
        return "(" + getName() + ";" + getName() + ")";
    }
}