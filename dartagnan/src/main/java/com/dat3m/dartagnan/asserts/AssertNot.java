package com.dat3m.dartagnan.asserts;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

public class AssertNot extends AbstractAssert {

    private final AbstractAssert child;

    public AssertNot(AbstractAssert child){
        this.child = child;
    }

    AbstractAssert getChild(){
        return child;
    }

    @Override
    public BoolExpr encode(Context ctx) {
        if(child != null){
            return ctx.mkNot(child.encode(ctx));
        }
        throw new RuntimeException("Empty assertion clause in " + this.getClass().getName());
    }

    @Override
    public String toString() {
        return "!" + child.toString();
    }
}
