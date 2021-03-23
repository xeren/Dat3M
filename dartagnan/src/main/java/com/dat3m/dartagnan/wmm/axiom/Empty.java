package com.dat3m.dartagnan.wmm.axiom;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;

import java.util.ArrayList;

public class Empty extends Axiom {

    public Empty(Relation rel) {
        super(rel);
    }

    public Empty(Relation rel, boolean negate) {
        super(rel, negate);
    }

	@Override
	public void getEncodeTupleSet(){
		ArrayList<Tuple> set = new ArrayList<>(rel.size());
		for(Tuple t : rel.getMaxTupleSet())
			set.add(t);
		rel.addEncodeTupleSet(set);
	}

	@Override
	public boolean test(boolean[][] e) {
		for(boolean[] a: e)
			for(boolean b: a)
				if(b)
					return false;
		return true;
	}

	@Override
    protected BoolExpr _consistent(Context ctx) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            enc = ctx.mkAnd(enc, ctx.mkNot(rel.edge(tuple)));
        }
        return enc;
    }

    @Override
    protected BoolExpr _inconsistent(Context ctx) {
        BoolExpr enc = ctx.mkFalse();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            enc = ctx.mkOr(enc, rel.edge(tuple));
        }
        return enc;
    }

    @Override
    protected String _toString() {
        return "empty " + rel.getName();
    }
}
