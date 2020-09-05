package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.*;

/**
 *
 * @author Florian Furbach
 */
public class RelComposition extends BinaryRelation {

    public static String makeTerm(Relation r1, Relation r2){
        return "(" + r1.getName() + ";" + r2.getName() + ")";
    }

    public RelComposition(Relation r1, Relation r2) {
        super(r1, r2);
        term = makeTerm(r1, r2);
    }

    public RelComposition(Relation r1, Relation r2, String name) {
        super(r1, r2, name);
        term = makeTerm(r1, r2);
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            TupleSet set1 = r1.getMaxTupleSet();
            TupleSet set2 = r2.getMaxTupleSet();
            for(Tuple rel1 : set1){
                for(Tuple rel2 : set2.getByFirst(rel1.getSecond())){
                    maxTupleSet.add(new Tuple(rel1.getFirst(), rel2.getSecond()));
                }
            }
        }
        return maxTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSetRecursive(){
        if(recursiveGroupId > 0 && maxTupleSet != null){
            TupleSet set1 = r1.getMaxTupleSetRecursive();
            TupleSet set2 = r2.getMaxTupleSetRecursive();
            for(Tuple rel1 : set1){
                for(Tuple rel2 : set2.getByFirst(rel1.getSecond())){
                    maxTupleSet.add(new Tuple(rel1.getFirst(), rel2.getSecond()));
                }
            }
            return maxTupleSet;
        }
        return getMaxTupleSet();
    }

    @Override
    public void addEncodeTupleSet(TupleSet tuples){
        Set<Tuple> activeSet = new HashSet<>(tuples);
        activeSet.removeAll(encodeTupleSet);
        encodeTupleSet.addAll(tuples);
        activeSet.retainAll(maxTupleSet);

        if(!activeSet.isEmpty()){
            TupleSet r1Set = new TupleSet();
            TupleSet r2Set = new TupleSet();

            Map<Integer, Set<Integer>> myMap = new HashMap<>();
            for(Tuple tuple : activeSet){
                int id1 = tuple.getFirst().getCId();
                int id2 = tuple.getSecond().getCId();
                myMap.putIfAbsent(id1, new HashSet<>());
                myMap.get(id1).add(id2);
            }

            for(Tuple tuple1 : r1.getMaxTupleSet()){
                Event e1 = tuple1.getFirst();
                Set<Integer> ends = myMap.get(e1.getCId());
                if(ends == null) continue;
                for(Tuple tuple2 : r2.getMaxTupleSet().getByFirst(tuple1.getSecond())){
                    Event e2 = tuple2.getSecond();
                    if(ends.contains(e2.getCId())){
                        r1Set.add(tuple1);
                        r2Set.add(tuple2);
                    }
                }
            }

            r1.addEncodeTupleSet(r1Set);
            r2.addEncodeTupleSet(r2Set);
        }
    }

    @Override
    protected BoolExpr encodeApprox(EncodeContext context) {
        BoolExpr enc = ctx.mkTrue();

        TupleSet r1Set = new TupleSet();
        r1Set.addAll(r1.getEncodeTupleSet());
        r1Set.retainAll(r1.getMaxTupleSet());

        TupleSet r2Set = new TupleSet();
        r2Set.addAll(r2.getEncodeTupleSet());
        r2Set.retainAll(r2.getMaxTupleSet());

        Map<Integer, BoolExpr> exprMap = new HashMap<>();
        for(Tuple tuple : encodeTupleSet){
            exprMap.put(tuple.hashCode(), ctx.mkFalse());
        }

        for(Tuple tuple1 : r1Set){
            Event e1 = tuple1.getFirst();
            Event e3 = tuple1.getSecond();
            for(Tuple tuple2 : r2Set.getByFirst(e3)){
                Event e2 = tuple2.getSecond();
                int id = Tuple.toHashCode(e1.getCId(), e2.getCId());
                if(exprMap.containsKey(id)){
                    BoolExpr e = exprMap.get(id);
                    e = ctx.mkOr(e, ctx.mkAnd(r1.edge(e1, e3), r2.edge(e3, e2)));
                    exprMap.put(id, e);
                }
            }
        }

        for(Tuple tuple : encodeTupleSet){
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), exprMap.get(tuple.hashCode())));
        }

        return enc;
    }

    @Override
    protected BoolExpr encodeIDL(EncodeContext e) {
        if(recursiveGroupId == 0)
            return encodeApprox(e);

        boolean recurseInR1 = (r1.getRecursiveGroupId() & recursiveGroupId) > 0;
        boolean recurseInR2 = (r2.getRecursiveGroupId() & recursiveGroupId) > 0;

        TupleSet r1Set = new TupleSet();
        r1Set.addAll(r1.getEncodeTupleSet());
        r1Set.retainAll(r1.getMaxTupleSet());

        TupleSet r2Set = new TupleSet();
        r2Set.addAll(r2.getEncodeTupleSet());
        r2Set.retainAll(r2.getMaxTupleSet());

        Map<Tuple,LinkedList<BoolExpr>> orClauseMap = new HashMap<>();
        Map<Tuple,LinkedList<BoolExpr>> idlClauseMap = new HashMap<>();
        for(Tuple tuple : encodeTupleSet){
            orClauseMap.put(tuple, new LinkedList<>());
            idlClauseMap.put(tuple, new LinkedList<>());
        }

        for(Tuple tuple1 : r1Set){
            Event e1 = tuple1.getFirst();
            Event e3 = tuple1.getSecond();
            for(Tuple tuple2 : r2Set.getByFirst(e3)){
                Event e2 = tuple2.getSecond();
                Tuple id = new Tuple(e1, e2);
                if(orClauseMap.containsKey(id)){
                    BoolExpr opt1 = e.edge(r1, e1, e3);
                    BoolExpr opt2 = e.edge(r2, e3, e2);
                    orClauseMap.get(id).add(e.and(opt1, opt2));

                    if(recurseInR1)
                        opt1 = e.and(opt1, e.lt(e.intCount(r1, e1, e2), e.intCount(this, e1, e3)));
                    if(recurseInR2)
                        opt2 = e.and(opt2, e.lt(e.intCount(r1, e1, e2), e.intCount(this, e3, e2)));
                    idlClauseMap.get(id).add(e.and(opt1, opt2));
                }
            }
        }

        return e.and(encodeTupleSet.stream().map(tuple->e.and(e.eq(e.edge(this, tuple), e.or(orClauseMap.get(tuple))), e.eq(e.edge(this, tuple), e.or(idlClauseMap.get(tuple))))));
    }

    @Override
    public BoolExpr encodeIteration(int groupId, int iteration){
        BoolExpr enc = ctx.mkTrue();

        if((groupId & recursiveGroupId) > 0 && iteration > lastEncodedIteration) {
            lastEncodedIteration = iteration;

            if(iteration == 0 && isRecursive){
                for(Tuple tuple : encodeTupleSet){
                    enc = ctx.mkAnd(ctx.mkNot(edge(iteration, tuple)));
                }
            } else {
                int childIteration = isRecursive ? iteration - 1 : iteration;

                boolean recurseInR1 = (r1.getRecursiveGroupId() & groupId) > 0;
                boolean recurseInR2 = (r2.getRecursiveGroupId() & groupId) > 0;

                java.util.function.BiFunction<?super Event,?super Event,?extends BoolExpr> edge1
                        = recurseInR1 ? (x,y)->r1.edge(childIteration, x, y) : r1::edge;
                java.util.function.BiFunction<?super Event,?super Event,?extends BoolExpr> edge2
                        = recurseInR2 ? (x,y)->r2.edge(childIteration, x, y) : r2::edge;

                TupleSet r1Set = new TupleSet();
                r1Set.addAll(r1.getEncodeTupleSet());
                r1Set.retainAll(r1.getMaxTupleSet());

                TupleSet r2Set = new TupleSet();
                r2Set.addAll(r2.getEncodeTupleSet());
                r2Set.retainAll(r2.getMaxTupleSet());

                Map<Integer, BoolExpr> exprMap = new HashMap<>();
                for(Tuple tuple : encodeTupleSet){
                    exprMap.put(tuple.hashCode(), ctx.mkFalse());
                }

                for(Tuple tuple1 : r1Set){
                    Event e1 = tuple1.getFirst();
                    Event e3 = tuple1.getSecond();
                    for(Tuple tuple2 : r2Set.getByFirst(e3)){
                        Event e2 = tuple2.getSecond();
                        int id = Tuple.toHashCode(e1.getCId(), e2.getCId());
                        if(exprMap.containsKey(id)){
                            BoolExpr e = exprMap.get(id);
                            e = ctx.mkOr(e, ctx.mkAnd(edge1.apply(e1, e3), edge2.apply(e3, e2)));
                            exprMap.put(id, e);
                        }
                    }
                }

                for(Tuple tuple : encodeTupleSet){
                    enc = ctx.mkAnd(enc, ctx.mkEq(edge(iteration, tuple), exprMap.get(tuple.hashCode())));
                }

                if(recurseInR1){
                    enc = ctx.mkAnd(enc, r1.encodeIteration(groupId, childIteration));
                }

                if(recurseInR2){
                    enc = ctx.mkAnd(enc, r2.encodeIteration(groupId, childIteration));
                }
            }
        }

        return enc;
    }

    @Override
    protected BoolExpr encodeFirstOrder(EncodeContext e) {
        EncodeContext.RelationPredicate edge = e.of(this);
        EncodeContext.RelationPredicate edge1 = e.of(r1);
        EncodeContext.RelationPredicate edge2 = e.of(r2);
        return e.forall(0, (a,c)->e.eq(edge.of(a, c), e.exists(2, b->e.and(edge1.of(a, b), edge2.of(b, c)),
                    b->e.pattern(edge1.of(a, b), edge2.of(b, c)))),
                (a,c)->e.pattern(edge.of(a, c)));
    }
}
