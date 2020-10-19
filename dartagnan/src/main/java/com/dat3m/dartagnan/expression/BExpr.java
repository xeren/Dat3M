package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.utils.Encoder;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.program.event.Event;

public abstract class BExpr implements ExprInterface {

    @Override
    public IntExpr toZ3Int(Event e, Encoder ctx) {
        return (IntExpr) ctx.mkITE(toZ3Bool(e, ctx), ctx.mkInt(1), ctx.mkInt(0));
    }

    @Override
    public int getIntValue(Event e, Encoder ctx, Model model){
        return getBoolValue(e, ctx, model) ? 1 : 0;
    }
}
