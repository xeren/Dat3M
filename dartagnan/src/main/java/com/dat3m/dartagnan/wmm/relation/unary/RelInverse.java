package com.dat3m.dartagnan.wmm.relation.unary;

import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import java.util.Collection;
import java.util.HashSet;

/**
 *
 * @author Florian Furbach
 */
public class RelInverse extends UnaryRelation {

    public static String makeTerm(Relation r1){
        return r1.getName() + "^-1";
    }

    public RelInverse(Relation r1){
        super(r1);
        term = makeTerm(r1);
    }

    public RelInverse(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

	@Override
	protected void mkMaxTupleSet(){
		for(Tuple pair : r1.getMaxTupleSet())
			addMaxTuple(pair.getSecond(),pair.getFirst(),pair.isMinimal());
	}

	@Override
	public void addEncodeTupleSet(Collection<Tuple> tuples){
        encodeTupleSet.addAll(tuples);
		HashSet<Tuple> activeSet = new HashSet<>(tuples);
        if(!activeSet.isEmpty()){
			HashSet<Tuple> invSet = new HashSet<>();
			for(Tuple t : activeSet)
				invSet.add(r1.of(t.getSecond(),t.getFirst()));
            r1.addEncodeTupleSet(invSet);
        }
    }

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1, e2), r1.edge(e2, e1)));
        }
        return enc;
    }
}
    

