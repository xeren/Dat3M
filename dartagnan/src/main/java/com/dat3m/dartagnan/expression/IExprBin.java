package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.utils.Encoder;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class IExprBin extends IExpr implements ExprInterface {

    private final ExprInterface lhs;
    private final ExprInterface rhs;
    private final IOpBin op;

    public IExprBin(ExprInterface lhs, IOpBin op, ExprInterface rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.op = op;
    }

    @Override
    public IntExpr toZ3Int(Event e, Encoder ctx) {
        return op.encode(lhs.toZ3Int(e, ctx), rhs.toZ3Int(e, ctx), ctx);
    }

    @Override
    public BoolExpr toZ3Bool(Event e, Encoder ctx) {
        return ctx.mkGt(toZ3Int(e, ctx), ctx.mkInt(0));
    }

    @Override
    public IntExpr getLastValueExpr(Encoder ctx){
        return op.encode(lhs.getLastValueExpr(ctx), rhs.getLastValueExpr(ctx), ctx);
    }

    @Override
    public ImmutableSet<Register> getRegs() {
        return new ImmutableSet.Builder<Register>().addAll(lhs.getRegs()).addAll(rhs.getRegs()).build();
    }

    @Override
    public String toString() {
        return "(" + lhs + " " + op + " " + rhs + ")";
    }

    @Override
    public int getIntValue(Event e, Encoder ctx, Model model){
        return op.combine(lhs.getIntValue(e, ctx, model), rhs.getIntValue(e, ctx, model));
    }
    
    @Override
	public IConst reduce() {
		int v1 = lhs.reduce().getValue();
		int v2 = rhs.reduce().getValue();
        switch(op){
        case PLUS:
            return new IConst(v1 + v2);
        case MINUS:
        	return new IConst(v1 - v2);
        case MULT:
        	return new IConst(v1 * v2);
        case DIV:
        	return new IConst(v1 / v2);
        case MOD:
        	return new IConst(v1 % v2);
        case AND:
        	return new IConst(v1 == 1 ? v2 : 0);
        case OR:
        	return new IConst(v1 == 1 ? 1 : v2);
        case XOR:
        	return new IConst(v1 + v2 == 1 ? 1 : 0);
		default:
			throw new UnsupportedOperationException("Reduce not supported for " + this);
        }
	}
}
