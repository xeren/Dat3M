package com.dat3m.dartagnan.expression.processing;

import java.math.BigInteger;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.memory.Location;


//TODO: This is buggy for now, because Addresses are treated as IConst
public class ExprSimplifier extends ExprTransformer {

    @Override
	public ExprInterface visit(IExprBin iBin) {
		var lhs = iBin.getLHS().visit(this);
		var rhs = iBin.getRHS().visit(this);
        IOpBin op = iBin.getOp();
        if (! (lhs instanceof IConst || rhs instanceof IConst)) {
            return new IExprBin(lhs, iBin.getOp(), rhs);
        } else if (lhs instanceof IConst && rhs instanceof IConst) {
            return new IExprBin(lhs, iBin.getOp(), rhs).reduce();
        }

        if (lhs instanceof IConst) {
            IConst lc = (IConst)lhs;
            BigInteger val = lc.getIntValue();
            switch (op) {
                case MULT:
                    return val.compareTo(BigInteger.ZERO) == 0 ? IConst.ZERO : val.equals(BigInteger.ONE) ? rhs : new IExprBin(lhs, op, rhs);
                case PLUS:
                    return val.compareTo(BigInteger.ZERO) == 0 ? rhs : new IExprBin(lhs, op, rhs);
                default:
                    return new IExprBin(lhs, op, rhs);
            }
        }

        IConst rc = (IConst)rhs;
        BigInteger val = rc.getIntValue();
        switch (op) {
            case MULT:
                return val.compareTo(BigInteger.ZERO) == 0 ? IConst.ZERO : val.equals(BigInteger.ONE) ? lhs : new IExprBin(lhs, op, rhs);
            case PLUS:
            case MINUS:
                return val.compareTo(BigInteger.ZERO) == 0 ? lhs : new IExprBin(lhs, op, rhs);
            default:
                return new IExprBin(lhs, op, rhs);
        }
    }

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
