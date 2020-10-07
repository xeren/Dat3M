package com.dat3m.dartagnan.program.event.rmw.cond;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.microsoft.z3.BoolExpr;

public class RMWReadCondUnless extends RMWReadCond implements RegWriter, RegReaderData {

    public RMWReadCondUnless(Register reg, ExprInterface cmp, IExpr address, String mo) {
        super(reg, cmp, address, mo);
    }

    @Override
    public BoolExpr getCond(EncodeContext e) {
        return e.not(e.eq(getMemValueExpr(e), cmp.toZ3Int(this, e)));
    }

    @Override
    public String condToString(){
        return "# if not " + resultRegister + " = " + cmp;
    }
}
