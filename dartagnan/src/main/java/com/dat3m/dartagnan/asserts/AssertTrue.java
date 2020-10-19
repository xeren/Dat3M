package com.dat3m.dartagnan.asserts;

import com.dat3m.dartagnan.utils.Encoder;
import com.microsoft.z3.BoolExpr;

public class AssertTrue extends AbstractAssert {

    @Override
    public BoolExpr encode(Encoder ctx) {
    	// We want the verification to succeed so it should be UNSAT
        return ctx.mkFalse();
    }

    @Override
    public String toString(){
        return "true";
    }
}
