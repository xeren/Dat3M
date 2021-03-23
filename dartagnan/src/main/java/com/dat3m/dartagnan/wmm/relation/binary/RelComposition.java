package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import java.util.*;
import java.util.function.BiFunction;

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
	protected void mkMaxTupleSet(){
		for(Tuple t1 : r1.getMaxTupleSet())
			for(Tuple t2 : r2.getMaxTupleSet().getByFirst(t1.getSecond()))
				addMaxTuple(t1.getFirst(),t2.getSecond());
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
	public void addEncodeTupleSet(Collection<Tuple> tuples){
		HashSet<Tuple> activeSet = new HashSet<>(tuples);
        activeSet.removeAll(encodeTupleSet);
        encodeTupleSet.addAll(tuples);
        activeSet.retainAll(maxTupleSet);

        if(!activeSet.isEmpty()){
			HashSet<Tuple> r1Set = new HashSet<>();
			HashSet<Tuple> r2Set = new HashSet<>();

			HashMap<Integer,HashSet<Integer>> myMap = new HashMap<>();
            for(Tuple tuple : activeSet){
                int id1 = tuple.getFirst().getCId();
                int id2 = tuple.getSecond().getCId();
                myMap.putIfAbsent(id1, new HashSet<>());
                myMap.get(id1).add(id2);
            }

            for(Tuple tuple1 : r1.getMaxTupleSet()){
                Event e1 = tuple1.getFirst();
				HashSet<Integer> ends = myMap.get(e1.getCId());
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
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

		HashSet<Tuple> r1Set = new HashSet<>(r1.getEncodeTupleSet());
        r1Set.retainAll(r1.getMaxTupleSet());

		HashMap<Event,HashSet<Event>> r2Set = new HashMap<>();
		HashSet<Event> empty = new HashSet<>();
		for(Tuple t : r2.getEncodeTupleSet())
			if(r2.getMaxTupleSet().contains(t))
				r2Set.computeIfAbsent(t.getFirst(),k->new HashSet<>()).add(t.getSecond());

        Map<Integer, BoolExpr> exprMap = new HashMap<>();
        for(Tuple tuple : encodeTupleSet){
            exprMap.put(tuple.hashCode(), ctx.mkFalse());
        }

        for(Tuple tuple1 : r1Set){
            Event e1 = tuple1.getFirst();
            Event e3 = tuple1.getSecond();
			for(Event e2 : r2Set.getOrDefault(e3,empty)){
                int id = Tuple.toHashCode(e1.getCId(), e2.getCId());
                if(exprMap.containsKey(id)){
                    BoolExpr e = exprMap.get(id);
                    e = ctx.mkOr(e, ctx.mkAnd(r1.edge(e1,e3), r2.edge(e3,e2)));
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
    protected BoolExpr encodeIDL() {
        if(recursiveGroupId == 0){
            return encodeApprox();
        }

        BoolExpr enc = ctx.mkTrue();

        boolean recurseInR1 = (r1.getRecursiveGroupId() & recursiveGroupId) > 0;
        boolean recurseInR2 = (r2.getRecursiveGroupId() & recursiveGroupId) > 0;

		HashSet<Tuple> r1Set = new HashSet<>(r1.getEncodeTupleSet());
        r1Set.retainAll(r1.getMaxTupleSet());

		HashMap<Event,HashSet<Event>> r2Set = new HashMap<>();
		HashSet<Event> empty = new HashSet<>();
		for(Tuple t : r2.getEncodeTupleSet())
			if(r2.getMaxTupleSet().contains(t))
				r2Set.computeIfAbsent(t.getFirst(),k->new HashSet<>()).add(t.getSecond());

        Map<Integer, BoolExpr> orClauseMap = new HashMap<>();
        Map<Integer, BoolExpr> idlClauseMap = new HashMap<>();
        for(Tuple tuple : encodeTupleSet){
            orClauseMap.put(tuple.hashCode(), ctx.mkFalse());
            idlClauseMap.put(tuple.hashCode(), ctx.mkFalse());
        }

        for(Tuple tuple1 : r1Set){
            Event e1 = tuple1.getFirst();
            Event e3 = tuple1.getSecond();
			for(Event e2 : r2Set.getOrDefault(e3,empty)){
                int id = Tuple.toHashCode(e1.getCId(), e2.getCId());
                if(orClauseMap.containsKey(id)){
                    BoolExpr opt1 = r1.edge(e1,e3);
                    BoolExpr opt2 = r2.edge(e3,e2);
                    orClauseMap.put(id, ctx.mkOr(orClauseMap.get(id), ctx.mkAnd(opt1, opt2)));

                    if(recurseInR1){
                        opt1 = ctx.mkAnd(opt1, ctx.mkGt(intCount(e1, e2), r1.intCount(e1, e3)));
                    }
                    if(recurseInR2){
                        opt2 = ctx.mkAnd(opt2, ctx.mkGt(intCount(e1, e2), r1.intCount(e3, e2)));
                    }
                    idlClauseMap.put(id, ctx.mkOr(idlClauseMap.get(id), ctx.mkAnd(opt1, opt2)));
                }
            }
        }

        for(Tuple tuple : encodeTupleSet){
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), orClauseMap.get(tuple.hashCode())));
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), idlClauseMap.get(tuple.hashCode())));
        }

        return enc;
    }

    @Override
    public BoolExpr encodeIteration(int groupId, int iteration){
        BoolExpr enc = ctx.mkTrue();

        if((groupId & recursiveGroupId) > 0 && iteration > lastEncodedIteration) {
            lastEncodedIteration = iteration;

            if(iteration == 0 && isRecursive){
                for(Tuple tuple : encodeTupleSet){
                    enc = ctx.mkAnd(ctx.mkNot(edge(iteration,tuple)));
                }
            } else {
                int childIteration = isRecursive ? iteration - 1 : iteration;

                boolean recurseInR1 = (r1.getRecursiveGroupId() & groupId) > 0;
                boolean recurseInR2 = (r2.getRecursiveGroupId() & groupId) > 0;

                BiFunction<Event,Event,BoolExpr> r1Edge = recurseInR1 ? (x,y)->r1.edge(childIteration,x,y) : r1::edge;
                BiFunction<Event,Event,BoolExpr> r2Edge = recurseInR2 ? (x,y)->r2.edge(childIteration,x,y) : r2::edge;

				HashSet<Tuple> r1Set = new HashSet<>(r1.getEncodeTupleSet());
                r1Set.retainAll(r1.getMaxTupleSet());

				HashMap<Event,HashSet<Event>> r2Set = new HashMap<>();
				HashSet<Event> empty = new HashSet<>();
				for(Tuple t : r2.getEncodeTupleSet())
					if(r2.getMaxTupleSet().contains(t))
						r2Set.computeIfAbsent(t.getFirst(),k->new HashSet<>()).add(t.getSecond());

                Map<Integer, BoolExpr> exprMap = new HashMap<>();
                for(Tuple tuple : encodeTupleSet){
                    exprMap.put(tuple.hashCode(), ctx.mkFalse());
                }

                for(Tuple tuple1 : r1Set){
                    Event e1 = tuple1.getFirst();
                    Event e3 = tuple1.getSecond();
					for(Event e2 : r2Set.getOrDefault(e3,empty)){
                        int id = Tuple.toHashCode(e1.getCId(), e2.getCId());
                        if(exprMap.containsKey(id)){
                            BoolExpr e = exprMap.get(id);
                            e = ctx.mkOr(e, ctx.mkAnd(r1Edge.apply(e1,e3),r2Edge.apply(e3,e2)));
                            exprMap.put(id, e);
                        }
                    }
                }

                for(Tuple tuple : encodeTupleSet){
                    enc = ctx.mkAnd(enc, ctx.mkEq(edge(iteration,tuple), exprMap.get(tuple.hashCode())));
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
}
