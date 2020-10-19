package com.dat3m.dartagnan.expression.op;

import com.dat3m.dartagnan.utils.Encoder;
import com.microsoft.z3.IntExpr;

public enum IOpUn {
    MINUS;

    @Override
    public String toString() {
        return "-";
    }

    public IntExpr encode(IntExpr e, Encoder ctx) {
        return (IntExpr)ctx.mkSub(ctx.mkInt(0),e);
    }
}
