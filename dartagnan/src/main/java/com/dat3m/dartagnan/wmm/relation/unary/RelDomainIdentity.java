package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.BoolExpr;
import java.util.Collection;
import java.util.HashSet;

public class RelDomainIdentity extends UnaryRelation {

    public static String makeTerm(Relation r1){
        return "[domain(" + r1.getName() + ")]";
    }

    public RelDomainIdentity(Relation r1){
        super(r1);
        term = makeTerm(r1);
    }

    public RelDomainIdentity(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

	@Override
	protected void mkMaxTupleSet(){
		for(Tuple t : r1.getMaxTupleSet()){
			Event e = t.getFirst();
			addMaxTuple(e,e,false);
		}
	}

	@Override
	public void addEncodeTupleSet(Collection<Tuple> tuples){
		HashSet<Tuple> activeSet = new HashSet<>(tuples);
		super.addEncodeTupleSet(activeSet);
        if(!activeSet.isEmpty()){
			HashSet<Tuple> r1Set = new HashSet<>();
            for(Tuple tuple : activeSet){
                r1Set.addAll(r1.getMaxTupleSet(tuple.getFirst()));
            }
            r1.addEncodeTupleSet(r1Set);
        }
    }

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple1 : encodeTupleSet){
            Event e = tuple1.getFirst();
            BoolExpr opt = ctx.mkFalse();
            for(Tuple tuple2 : r1.getMaxTupleSet(e)){
                opt = ctx.mkOr(r1.edge(tuple2));
            }
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e, e), opt));
        }
        return enc;
    }
}
