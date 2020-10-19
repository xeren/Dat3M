package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.utils.Encoder;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.expression.op.IOpUn;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class IExprUn extends IExpr {

    private final ExprInterface b;
    private final IOpUn op;

    public IExprUn(IOpUn op, ExprInterface b) {
        this.b = b;
        this.op = op;
    }

	@Override
	public IntExpr toZ3Int(Event e, Encoder ctx) {
		return op.encode(b.toZ3Int(e, ctx), ctx);
	}

	@Override
	public IntExpr getLastValueExpr(Encoder ctx) {
        return op.encode(b.getLastValueExpr(ctx), ctx);
	}

	@Override
	public int getIntValue(Event e, Encoder ctx, Model model) {
        return -(b.getIntValue(e, ctx, model));
	}

	@Override
	public ImmutableSet<Register> getRegs() {
        return new ImmutableSet.Builder<Register>().addAll(b.getRegs()).build();
	}

    @Override
    public String toString() {
        return "(" + op + b + ")";
    }

    @Override
	public IConst reduce() {
        switch(op){
		case MINUS:
			return new IConst(-b.reduce().getValue());
		default:
			throw new UnsupportedOperationException("Reduce not supported for " + this);
        }
	}
}
