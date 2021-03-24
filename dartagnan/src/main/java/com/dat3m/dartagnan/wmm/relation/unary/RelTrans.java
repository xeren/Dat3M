package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntExpr;
import java.util.*;

/**
 *
 * @author Florian Furbach
 */
public class RelTrans extends UnaryRelation {

    private HashSet<Tuple> fullEncodeTupleSet;

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
        fullEncodeTupleSet = new HashSet<>();
    }

	@Override
	protected void mkMaxTupleSet(){
		for(Map.Entry<Event,HashSet<Event>> e : r1.getMaxTupleSetTransitive().entrySet()){
			Event e1 = e.getKey();
			for(Event e2 : e.getValue())
				addMaxTuple(e1,e2,false);
		}
		for(Tuple t : r1.getMaxTupleSet())
			if(t.isMinimal())
				addMaxTuple(t.getFirst(),t.getSecond(),true);
	}

	@Override
	public void addEncodeTupleSet(Collection<Tuple> tuples){
		HashSet<Tuple> activeSet = new HashSet<>(tuples);
        activeSet.removeAll(encodeTupleSet);
        encodeTupleSet.addAll(activeSet);

		HashSet<Tuple> processNow = activeSet;
		HashSet<Tuple> result = new HashSet<>();
		while(!processNow.isEmpty()) {
			HashSet<Tuple> processNext = new HashSet<>();
			result.addAll(processNow);
			for (Tuple tuple : processNow) {
				Event e1 = tuple.getFirst();
				Event e2 = tuple.getSecond();
				for(Event e3 : r1.getMaxTupleSetTransitive().get(e1)) {
					if (e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId()
							&& r1.getMaxTupleSetTransitive().get(e3).contains(e2)) {
						processNext.add(new Tuple(e1, e3));
						processNext.add(new Tuple(e3, e2));
					}
				}
			}
			processNext.removeAll(result);
			processNow = processNext;
		}
		if(fullEncodeTupleSet.addAll(result)){
			ArrayList<Tuple> a = new ArrayList<>(result.size());
			for(Tuple t : result){
				Tuple tt = r1.of(t.getFirst(),t.getSecond());
				if(null!=tt)
					a.add(tt);
			}
			r1.addEncodeTupleSet(a);
		}
	}

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : fullEncodeTupleSet){
            BoolExpr orClause = ctx.mkFalse();

            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

			if(r1.contains(e1,e2)){
                orClause = ctx.mkOr(orClause, r1.edge(e1, e2));
            }

            for(Event e3 : r1.getMaxTupleSetTransitive().get(e1)){
                if(e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId() && r1.getMaxTupleSetTransitive().get(e3).contains(e2)){
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
    protected BoolExpr encodeIDL() {
        BoolExpr enc = ctx.mkTrue();

		HashMap<Integer,LinkedList<Event>> byFirst = new HashMap<>();
		for(Tuple t : fullEncodeTupleSet)
			byFirst.computeIfAbsent(t.getFirst().getCId(),k->new LinkedList<>()).add(t.getSecond());

        for(Tuple tuple : fullEncodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            BoolExpr orClause = ctx.mkFalse();
            for(Event e3 : byFirst.get(e1.getCId())){
                if(!e3.equals(e2)) {
                    if (r1.getMaxTupleSetTransitive().get(e3).contains(e2)) {
                        orClause = ctx.mkOr(orClause, ctx.mkAnd(
                                edge(e1, e3),
                                edge(e3, e2),
                                ctx.mkGt(idlConcatIntCount(e1, e2), intCount(e1, e3)),
                                ctx.mkGt(idlConcatIntCount(e1, e2), intCount(e3, e2))));
                    }
                }
            }

            enc = ctx.mkAnd(enc, ctx.mkEq(idlConcatEdge(e1, e2), orClause));

            orClause = ctx.mkFalse();
            for(Event e3 : byFirst.get(e1.getCId())){
                if(!e3.equals(e2)) {
                    if (r1.getMaxTupleSetTransitive().get(e3).contains(e2)) {
                        orClause = ctx.mkOr(orClause, ctx.mkAnd(
                                edge(e1, e3),
                                edge(e3, e2)));
                    }
                }
            }

            enc = ctx.mkAnd(enc, ctx.mkEq(idlConcatEdge(e1, e2), orClause));

            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1, e2), ctx.mkOr(
                    r1.edge(e1,e2),
                    ctx.mkAnd(idlConcatEdge(e1, e2), ctx.mkGt(intCount(e1, e2), idlConcatIntCount(e1, e2)))
            )));

            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1,e2), ctx.mkOr(
                    r1.edge(e1,e2),
                    idlConcatEdge(e1, e2)
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
                        currentTupleMap.get(newTuple).add(
                            ctx.mkAnd(r1.edge(iteration, e1, e3), r1.edge(iteration, e3, e2)));

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

                enc = ctx.mkAnd(enc, ctx.mkEq(r1.edge(iteration, tuple), orClause));
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

    private BoolExpr idlConcatEdge(Event first, Event second) {
        return ctx.mkBoolConst("concat "+getName()+" "+first.getCId()+" "+second.getCId());
    }

    private IntExpr idlConcatIntCount(Event first, Event second) {
        return ctx.mkIntConst("concat-level "+getName()+" "+first.getCId()+" "+second.getCId());
    }
}