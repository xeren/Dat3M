package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;

public class RelEmpty extends Relation {

    public RelEmpty(String name) {
        super(name);
        term = name;
    }

	@Override
	protected void mkMaxTupleSet(){
	}

    @Override
    protected BoolExpr encodeApprox() {
        return ctx.mkTrue();
    }
}
