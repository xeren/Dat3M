package com.dat3m.dartagnan.program.arch.aarch64.event;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.utils.RegWriter;

public class RMWStoreExclusiveStatus extends Event implements RegWriter {

    private final Register register;
    private final RMWStoreExclusive storeEvent;
    private IntExpr regResultExpr;

    RMWStoreExclusiveStatus(Register register, RMWStoreExclusive storeEvent){
        this.register = register;
        this.storeEvent = storeEvent;
        addFilters(EType.ANY, EType.VISIBLE, EType.LOCAL, EType.REG_WRITER);
    }

    @Override
    public void initialise(EncodeContext ctx) {
        super.initialise(ctx);
        regResultExpr = register.toZ3IntResult(this, ctx);
    }

    @Override
    public Register getResultRegister(){
        return register;
    }

    @Override
    public IntExpr getResultRegisterExpr(){
        return regResultExpr;
    }

    @Override
    public String toString() {
        return register + " <- status(" + storeEvent.toStringBase() + ")";
    }

    @Override
    protected BoolExpr encodeExec(EncodeContext e){
        return e.and(
          super.encodeExec(e),
          e.implies(storeEvent.exec(), e.eq(regResultExpr, new IConst(0).toZ3Int(this, e))),
          e.or(storeEvent.exec(), e.eq(regResultExpr, e.context.mkInt(1))));
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void unroll(int bound, Event predecessor) {
        throw new RuntimeException("RMWStoreExclusiveStatus cannot be unrolled: event must be generated during compilation");
    }
}
