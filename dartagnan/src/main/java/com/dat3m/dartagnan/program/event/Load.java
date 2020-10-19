package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.utils.Encoder;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.utils.EType;

public class Load extends MemEvent implements RegWriter {

    protected final Register resultRegister;

    public Load(Register register, IExpr address, String mo) {
        super(address, mo);
        this.resultRegister = register;
        addFilters(EType.ANY, EType.VISIBLE, EType.MEMORY, EType.READ, EType.REG_WRITER);
    }

    protected Load(Load other){
        super(other);
        this.resultRegister = other.resultRegister;
    }

    @Override
    public void initialise(Encoder ctx) {
        super.initialise(ctx);
        memValueExpr = ctx.result(resultRegister, this);
    }

    @Override
    public Register getResultRegister(){
        return resultRegister;
    }

    @Override
    public IntExpr getResultRegisterExpr(){
        return memValueExpr;
    }

    @Override
    public String toString() {
        return resultRegister + " = load(*" + address + (mo != null ? ", " + mo : "") + ")";
    }

    @Override
    public String label(){
        return "R" + (mo != null ? "_" + mo : "");
    }

    @Override
    public ExprInterface getMemValue(){
        return resultRegister;
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public Load getCopy(){
        return new Load(this);
    }
}
