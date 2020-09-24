package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public interface ExprInterface {

	IConst reduce();

	IntExpr toZ3Int(Event e, EncodeContext c);

	BoolExpr toZ3Bool(Event e, EncodeContext c);

	IntExpr getLastValueExpr(EncodeContext c);

	int getIntValue(Event e, EncodeContext c, Model m);

	boolean getBoolValue(Event e, EncodeContext c, Model m);

	ImmutableSet<Register> getRegs();
}
