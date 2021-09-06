package com.dat3m.dartagnan.expression.processing;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.memory.Location;


//TODO: This is buggy for now, because Addresses are treated as IConst
public class ExprSimplifier extends ExprTransformer {

    @Override
	public ExprInterface visit(IExprUn iUn) {
        return new IExprUn(iUn.getOp(), iUn.getInner());
    }

    @Override
	public ExprInterface visit(INonDet iNonDet) {
        return iNonDet;
    }

    @Override
    public ExprInterface visit(Location loc) {
        return loc;
    }

    @Override
    public ExprInterface visit(Register reg) {
        return reg;
    }

    @Override
    public ExprInterface visit(Address address) {
        return address;
    }
}
