package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.Collection;
import java.util.HashSet;
import java.util.function.BiFunction;

/**
 *
 * @author Florian Furbach
 */
public class RelMinus extends BinaryRelation {

    public static String makeTerm(Relation r1, Relation r2){
        return "(" + r1.getName() + "\\" + r2.getName() + ")";
    }

    public RelMinus(Relation r1, Relation r2) {
        super(r1, r2);
        term = makeTerm(r1, r2);
    }

    public RelMinus(Relation r1, Relation r2, String name) {
        super(r1, r2, name);
        term = makeTerm(r1, r2);
    }

    @Override
    public void initialise(Program program, Context ctx, Settings settings){
        super.initialise(program, ctx, settings);
        if(r2.getRecursiveGroupId() > 0){
            throw new RuntimeException("Relation " + r2.getName() + " cannot be recursive since it occurs in a set minus.");
        }
    }

	@Override
	protected void mkMaxTupleSet(){
		maxTupleSet.addAll(r1.getMaxTupleSet());
		r2.getMaxTupleSet();
	}

	@Override
	protected void updateMaxTupleSetRecursive(){
		r1.getMaxTupleSetRecursive();
		mkMaxTupleSet();
	}

	@Override
	public void addEncodeTupleSet(Collection<Tuple> tuples){
		HashSet<Tuple> activeSet = new HashSet<>(tuples);
		activeSet.removeAll(encodeTupleSet);
		encodeTupleSet.addAll(activeSet);
		activeSet.retainAll(maxTupleSet);
		if(!activeSet.isEmpty()){
			r1.addEncodeTupleSet(activeSet);
			activeSet.retainAll(r2.getMaxTupleSet());
			r2.addEncodeTupleSet(activeSet);
		}
	}

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            BoolExpr opt1 = r1.edge(e1, e2);
            BoolExpr opt2 = not2(e1,e2);
            if (Relation.PostFixApprox) {
                enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.mkAnd(opt1, opt2), edge(e1, e2)));
            } else {
                enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1, e2), ctx.mkAnd(opt1, opt2)));
            }
        }
        return enc;
    }

    @Override
    protected BoolExpr encodeIDL() {
        if(recursiveGroupId == 0){
            return encodeApprox();
        }

        BoolExpr enc = ctx.mkTrue();

        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();

            BoolExpr opt1 = r1.edge(e1, e2);
            BoolExpr opt2 = not2(e1,e2);
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1, e2), ctx.mkAnd(opt1, opt2)));

            opt1 = ctx.mkAnd(opt1, ctx.mkGt(intCount(e1, e2), r1.intCount(e1, e2)));
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1, e2), ctx.mkAnd(opt1, opt2)));
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
                    enc = ctx.mkAnd(ctx.mkNot(edge(iteration, tuple)));
                }
            } else {
                int childIteration = isRecursive ? iteration - 1 : iteration;
                boolean recurse = (r1.getRecursiveGroupId() & groupId) > 0;

                BiFunction<Event,Event,BoolExpr> r1Edge = recurse ? (x,y)->r1.edge(childIteration,x,y) : r1::edge;

                for(Tuple tuple : encodeTupleSet){
                    Event e1 = tuple.getFirst();
                    Event e2 = tuple.getSecond();
                    enc = ctx.mkAnd(enc, ctx.mkEq(edge(iteration,e1,e2),ctx.mkAnd(r1Edge.apply(e1,e2),not2(e1,e2))));
                }

                if(recurse){
                    enc = ctx.mkAnd(enc, r1.encodeIteration(groupId, childIteration));
                }
            }
        }

        return enc;
    }

	private BoolExpr not2(Event first, Event second){
		return r2.contains(first,second) ? ctx.mkNot(r2.edge(first,second)) : ctx.mkTrue();
	}
}
