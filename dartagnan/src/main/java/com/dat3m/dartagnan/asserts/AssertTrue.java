package com.dat3m.dartagnan.asserts;

import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.BoolExpr;

public class AssertTrue extends AbstractAssert {

    @Override
    public BoolExpr encode(EncodeContext c) {
    	// We want the verification to succeed so it should be UNSAT
        return c.or();
    }

    @Override
    public String toString(){
        return "true";
    }
}
