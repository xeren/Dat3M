package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.utils.Encoder;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public interface ExprInterface {

	IConst reduce();
	
    IntExpr toZ3Int(Event e, Encoder ctx);

    BoolExpr toZ3Bool(Event e, Encoder ctx);

    IntExpr getLastValueExpr(Encoder ctx);

    int getIntValue(Event e, Encoder ctx, Model model);

    boolean getBoolValue(Event e, Encoder ctx, Model model);

    ImmutableSet<Register> getRegs();
    
}
