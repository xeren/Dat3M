package com.dat3m.dartagnan.wmm.axiom;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 *
 * @author Florian Furbach
 */
public class Irreflexive extends Axiom {

    public Irreflexive(Relation rel) {
        super(rel);
    }

    public Irreflexive(Relation rel, boolean negate) {
        super(rel, negate);
    }

	@Override
	public void getEncodeTupleSet(){
        TupleSet set = new TupleSet();
        for(Tuple tuple : rel.getMaxTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                set.add(tuple);
            }
        }
		rel.addEncodeTupleSet(set);
	}

    @Override
    protected BoolExpr _consistent(Context ctx) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                enc = ctx.mkAnd(enc, ctx.mkNot(rel.edge(tuple.getFirst(), tuple.getFirst())));
            }
        }
        return enc;
    }

    @Override
    protected BoolExpr _inconsistent(Context ctx) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                enc = ctx.mkOr(enc, rel.edge(tuple.getFirst(), tuple.getFirst()));
            }
        }
        return enc;
    }

    @Override
    protected String _toString() {
        return "irreflexive " + rel.getName();
    }
}
