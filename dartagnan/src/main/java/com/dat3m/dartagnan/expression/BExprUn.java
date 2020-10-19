package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.utils.Encoder;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.expression.op.BOpUn;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class BExprUn extends BExpr {

    private final ExprInterface b;
    private final BOpUn op;

    public BExprUn(BOpUn op, ExprInterface b) {
        this.b = b;
        this.op = op;
    }

    @Override
    public BoolExpr toZ3Bool(Event e, Encoder ctx) {
        return op.encode(b.toZ3Bool(e, ctx), ctx);
    }

    @Override
    public IntExpr getLastValueExpr(Encoder ctx){
        BoolExpr expr = ctx.mkGt(b.getLastValueExpr(ctx), ctx.mkInt(1));
        return (IntExpr)ctx.mkITE(op.encode(expr, ctx), ctx.mkInt(1), ctx.mkInt(0));
    }

    @Override
    public ImmutableSet<Register> getRegs() {
        return b.getRegs();
    }

    @Override
    public String toString() {
        return "(" + op + " " + b + ")";
    }

    @Override
    public boolean getBoolValue(Event e, Encoder ctx, Model model){
        return op.combine(b.getBoolValue(e, ctx, model));
    }

	@Override
	public IConst reduce() {
		return new IConst(b.reduce().getValue());
	}
}
