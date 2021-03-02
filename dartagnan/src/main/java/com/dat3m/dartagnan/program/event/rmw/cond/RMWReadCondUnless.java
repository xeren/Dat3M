package com.dat3m.dartagnan.program.event.rmw.cond;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.ControlBlock;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.microsoft.z3.Context;

public class RMWReadCondUnless extends RMWReadCond implements RegWriter, RegReaderData {

    public RMWReadCondUnless(Register reg, ExprInterface cmp, IExpr address, String mo) {
        super(reg, cmp, address, mo);
    }

	@Override
	public ControlBlock initialise(Context c, ControlBlock b, ControlMessage m) {
		z3Cond = c.mkNot(z3Cond);
		return super.initialise(c,b,m);
	}

    @Override
    public String condToString(){
        return "# if not " + resultRegister + " = " + cmp;
    }
}
