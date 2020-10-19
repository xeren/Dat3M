package com.dat3m.dartagnan.expression.op;

import com.dat3m.dartagnan.utils.Encoder;
import com.microsoft.z3.BoolExpr;

public enum BOpUn {
    NOT;

    @Override
    public String toString() {
       	return "!";
    }

    public BoolExpr encode(BoolExpr e, Encoder ctx) {
       	return ctx.mkNot(e);
    }

    public boolean combine(boolean a){
       	return !a;
    }
}
