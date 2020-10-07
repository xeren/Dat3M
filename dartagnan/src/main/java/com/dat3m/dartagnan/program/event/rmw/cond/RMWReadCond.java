package com.dat3m.dartagnan.program.event.rmw.cond;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.rmw.RMWLoad;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.BoolExpr;
import java.util.Set;

public abstract class RMWReadCond extends RMWLoad implements RegWriter, RegReaderData {

    protected ExprInterface cmp;
    private Set<Register> dataRegs;

    RMWReadCond(Register reg, ExprInterface cmp, IExpr address, String atomic) {
        super(reg, address, atomic);
        this.cmp = cmp;
        this.dataRegs = Register.of(cmp);
        addFilters(EType.REG_READER);
    }

    public abstract BoolExpr getCond(EncodeContext context);

    @Override
    public Set<Register> getDataRegs(){
        return dataRegs;
    }

    public abstract String condToString();

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void unroll(int bound, Event predecessor) {
        throw new RuntimeException("RMWReadCond cannot be unrolled: event must be generated during compilation");
    }
}
