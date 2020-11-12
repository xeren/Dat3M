package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.Computation;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.*;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;
import static com.dat3m.dartagnan.wmm.utils.Utils.intCount;

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
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : fullEncodeTupleSet){
            BoolExpr orClause = ctx.mkFalse();

            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            if(r1.getMaxTupleSet().contains(new Tuple(e1, e2))){
                orClause = ctx.mkOr(orClause, Utils.edge(r1.getName(), e1, e2, ctx));
            }

            for(Event e3 : transitiveReachabilityMap.get(e1)){
                if(e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId() && transitiveReachabilityMap.get(e3).contains(e2)){
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

            BoolExpr orClause = ctx.mkFalse();
            for(Tuple tuple2 : fullEncodeTupleSet.getByFirst(e1)){
                if (!tuple2.equals(tuple)) {
                    Event e3 = tuple2.getSecond();
                    if (transitiveReachabilityMap.get(e3).contains(e2)) {
                        orClause = ctx.mkOr(orClause, ctx.mkAnd(
                                edge(this.getName(), e1, e3, ctx),
                                edge(this.getName(), e3, e2, ctx),
                                ctx.mkGt(intCount(this.idlConcatName(), e1, e2, ctx), intCount(this.getName(), e1, e3, ctx)),
                                ctx.mkGt(intCount(this.idlConcatName(), e1, e2, ctx), intCount(this.getName(), e3, e2, ctx))));
                    }
                }
            }

            enc = ctx.mkAnd(enc, ctx.mkEq(edge(this.idlConcatName(), e1, e2, ctx), orClause));

            orClause = ctx.mkFalse();
            for(Tuple tuple2 : fullEncodeTupleSet.getByFirst(e1)){
                if (!tuple2.equals(tuple)) {
                    Event e3 = tuple2.getSecond();
                    if (transitiveReachabilityMap.get(e3).contains(e2)) {
                        orClause = ctx.mkOr(orClause, ctx.mkAnd(
                                edge(this.getName(), e1, e3, ctx),
                                edge(this.getName(), e3, e2, ctx)));
                    }
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
            enc = ctx.mkAnd(enc, ctx.mkEq(
                    Utils.edge(getName(), tuple.getFirst(), tuple.getSecond(), ctx),
                    Utils.edge(r1.getName() + "_" + iteration, tuple.getFirst(), tuple.getSecond(), ctx)
            ));
        }

        return enc;
    }

    @Override
    public Computation.Relation register(Computation computation) {
        if(computation.relation.containsKey(this))
            return computation.relation.get(this);
        Computation.Relation c1 = r1.register(computation);
        Computation.Relation r = new Computation.Relation();
        computation.relation.put(this, r);
        c1.addParent((x,y)->{
            r.addMax(x,y);
            if(x!=y) {
                r.maxByFirst(y).forEach(z->r.addMax(x,z));
                r.maxBySecond(x).forEach(z->r.addMax(z,y));
            }});
        return r;
    }

    private static class Pair {
        final com.dat3m.dartagnan.wmm.Event x;
        final com.dat3m.dartagnan.wmm.Event z;
        Pair(com.dat3m.dartagnan.wmm.Event x, com.dat3m.dartagnan.wmm.Event z) {
            this.x = x;
            this.z = z;
        }
    }

    @Override
    public BoolExpr encode(Context c, Computation r, List<BoolExpr> o, com.dat3m.dartagnan.wmm.Event x, com.dat3m.dartagnan.wmm.Event z) {
        Computation.Relation rel = r.relation.get(this);
        if(rel.encode(x, z)) {
            Stack<Pair> active = new Stack<>();
            active.push(new Pair(x, z));
            while(!active.empty()){
                Pair p = active.pop();
                ArrayList<BoolExpr> opt = new ArrayList<>();
                if(r.relation.get(r1).hasMax(p.x, p.z))
                    opt.add(r1.encode(c, r, o, p.x, p.z));
                rel.maxByFirst(p.x).forEach(y->{
                    if(p.x != y && p.z != y && rel.hasMax(y, p.z)) {
                        //mark x-y as encoded
                        if(rel.encode(p.x, y))
                            active.push(new Pair(p.x, y));
                        //mark y-z as encoded
                        if(rel.encode(y, p.z))
                            active.push(new Pair(y, p.z));
                        opt.add(c.mkAnd(encode(c, r, o, p.x, y), encode(c, r, o, y, p.z)));
                    }
                });
                o.add(c.mkEq(c.mkBoolConst(getName() + " " + p.x.id + " " + p.z.id), c.mkOr(opt.toArray(new BoolExpr[0]))));
            }
        }
        return c.mkBoolConst(getName() + " " + x.id + " " + z.id);
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