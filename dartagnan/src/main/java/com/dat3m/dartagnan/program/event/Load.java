package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

public class Load extends MemEvent implements RegWriter {

    protected final Register resultRegister;

    public Load(Register register, IExpr address, String... tag) {
        super(address);
        this.resultRegister = register;
        addFilters(EType.ANY, EType.VISIBLE, EType.MEMORY, EType.READ);
        addFilters(tag);
    }

    protected Load(Load other){
        super(other);
        this.resultRegister = other.resultRegister;
    }

    @Override
    public void encode(Context c, RuleAcceptor out, BoolExpr in) {
        super.encode(c, out, in);
        memValueExpr = resultRegister.toZ3IntResult(this, c);
    }

    @Override
    public Register getResultRegister(){
        return resultRegister;
    }

    @Override
    public Expr getResultRegisterExpr(){
        return memValueExpr;
    }

    @Override
    public String toString() {
        return resultRegister + " = load(*" + address + ")";
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
