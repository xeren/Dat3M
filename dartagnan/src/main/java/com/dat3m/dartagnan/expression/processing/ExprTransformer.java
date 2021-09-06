package com.dat3m.dartagnan.expression.processing;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.memory.Location;


public abstract class ExprTransformer implements ExpressionVisitor<ExprInterface> {

    @Override
    public ExprInterface visit(Atom atom) {
        return new Atom(atom.getLHS().visit(this), atom.getOp(), atom.getRHS().visit(this));
    }

    @Override
    public ExprInterface visit(BExprBin bBin) {
		return BExprBin.of(bBin.getOp(), bBin.getLHS().visit(this), bBin.getRHS().visit(this));
    }

    @Override
    public ExprInterface visit(BExprUn bUn) {
		return BExprUn.of(bUn.getOp(), bUn.getInner().visit(this));
    }

    @Override
    public ExprInterface visit(BNonDet bNonDet) {
        return bNonDet;
    }

    @Override
	public ExprInterface visit(IConst iConst) {
        return iConst;
    }

    @Override
	public ExprInterface visit(IExprBin iBin) {
        return new IExprBin(iBin.getLHS().visit(this), iBin.getOp(), iBin.getRHS().visit(this));
    }

    @Override
	public ExprInterface visit(IExprUn iUn) {
        return new IExprUn(iUn.getOp(), iUn.getInner().visit(this));
    }

    @Override
    public ExprInterface visit(IfExpr ifExpr) {
        return new IfExpr(ifExpr.getGuard().visit(this), ifExpr.getTrueBranch().visit(this), ifExpr.getFalseBranch().visit(this));
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
