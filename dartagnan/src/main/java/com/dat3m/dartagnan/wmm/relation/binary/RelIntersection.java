package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.BoolExpr;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.BiFunction;

/**
 *
 * @author Florian Furbach
 */
public class RelIntersection extends BinaryRelation {

    public static String makeTerm(Relation r1, Relation r2){
        return "(" + r1.getName() + "&" + r2.getName() + ")";
    }

    public RelIntersection(Relation r1, Relation r2) {
        super(r1, r2);
        term = makeTerm(r1, r2);
    }

    public RelIntersection(Relation r1, Relation r2, String name) {
        super(r1, r2, name);
        term = makeTerm(r1, r2);
    }

	@Override
	protected void mkMaxTupleSet(){
		maxTupleSet.addAll(r1.getMaxTupleSet());
		maxTupleSet.retainAll(r2.getMaxTupleSet());
	}

	@Override
	protected void updateMaxTupleSetRecursive(){
		r1.getMaxTupleSetRecursive();
		r2.getMaxTupleSetRecursive();
		mkMaxTupleSet();
	}

	@Override
	public void addEncodeTupleSet(Collection<Tuple> tuples){
		HashSet<Tuple> activeSet = new HashSet<>(tuples);
		activeSet.removeAll(encodeTupleSet);
		encodeTupleSet.addAll(activeSet);
		if(!activeSet.isEmpty()){
			r1.addEncodeTupleSet(activeSet);
			r2.addEncodeTupleSet(activeSet);
		}
	}

    @Override
    public BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1,e2), ctx.mkAnd(r1.edge(e1,e2), r2.edge(e1,e2))));
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

        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            BoolExpr opt1 = r1.edge(e1,e2);
            BoolExpr opt2 = r2.edge(e1,e2);
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1,e2), ctx.mkAnd(opt1, opt2)));

            if(recurseInR1){
                opt1 = ctx.mkAnd(opt1, ctx.mkGt(intCount(e1, e2), r1.intCount(e1, e2)));
            }
            if(recurseInR2){
                opt2 = ctx.mkAnd(opt2, ctx.mkGt(intCount(e1, e2), r2.intCount(e1, e2)));
            }
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1,e2), ctx.mkAnd(opt1, opt2)));
        }
        return enc;
    }

    @Override
    public BoolExpr encodeIteration(int groupId, int iteration){
        BoolExpr enc = ctx.mkTrue();

        if((groupId & recursiveGroupId) > 0 && iteration > lastEncodedIteration){
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

                for(Tuple tuple : encodeTupleSet){
                    Event e1 = tuple.getFirst();
                    Event e2 = tuple.getSecond();
                    enc = ctx.mkAnd(enc, ctx.mkEq(edge(iteration,e1,e2),
                        ctx.mkAnd(r1Edge.apply(e1,e2),r2Edge.apply(e1,e2))));
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
