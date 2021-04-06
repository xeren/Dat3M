package com.dat3m.dartagnan.program.arch.aarch64.event;

import com.dat3m.dartagnan.program.ControlBlock;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.utils.RegWriter;

public class RMWStoreExclusiveStatus extends Event implements RegWriter {

    private final Register register;
    private final RMWStoreExclusive storeEvent;
    private Expr regResultExpr;

    RMWStoreExclusiveStatus(Register register, RMWStoreExclusive storeEvent){
        this.register = register;
        this.storeEvent = storeEvent;
        addFilters(EType.ANY, EType.VISIBLE, EType.LOCAL, EType.REG_WRITER);
    }

	@Override
	public ControlBlock initialise(Context c, ControlBlock b, ControlMessage m) {
		ControlBlock r = super.initialise(c,b,m);
		regResultExpr = register.toZ3IntResult(this,c);
		execVar = c.mkBoolConst("exec("+repr()+")");
		return r;
	}

    @Override
    public Register getResultRegister(){
        return register;
    }

    @Override
    public Expr getResultRegisterExpr(){
        return regResultExpr;
    }

    @Override
    public String toString() {
        return register + " <- status(" + storeEvent.toStringBase() + ")";
    }

    @Override
    protected BoolExpr encodeExec(Context ctx){
        int precision = register.getPrecision();
		BoolExpr enc = ctx.mkAnd(
                ctx.mkImplies(storeEvent.exec(), ctx.mkEq(regResultExpr, new IConst(0, precision).toZ3Int(this, ctx))),
                ctx.mkImplies(ctx.mkNot(storeEvent.exec()), ctx.mkEq(regResultExpr, new IConst(1, precision).toZ3Int(this, ctx)))
        );
        return ctx.mkAnd(super.encodeExec(ctx), enc);
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void unroll(int bound, Event predecessor) {
        throw new RuntimeException("RMWStoreExclusiveStatus cannot be unrolled: event must be generated during compilation");
    }
}
