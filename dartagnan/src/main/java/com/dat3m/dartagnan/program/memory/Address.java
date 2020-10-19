package com.dat3m.dartagnan.program.memory;

import com.dat3m.dartagnan.utils.Encoder;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class Address extends IConst implements ExprInterface {

    private final int index;
    private Integer constValue;

    Address(int index){
        super(index);
        this.index = index;
    }

    @Override
    public ImmutableSet<Register> getRegs(){
        return ImmutableSet.of();
    }

    @Override
    public IntExpr toZ3Int(Event e, Encoder ctx){
        return toZ3Int(ctx);
    }

    @Override
    public IntExpr getLastValueExpr(Encoder ctx){
        return toZ3Int(ctx);
    }

    @Override
    public BoolExpr toZ3Bool(Event e, Encoder ctx){
        return ctx.mkTrue();
    }

    @Override
    public String toString(){
        return "&mem" + index;
    }

    @Override
    public int hashCode(){
        return index;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

        return index == ((Address)obj).index;
    }

    @Override
    public IntExpr toZ3Int(Encoder ctx){
        return ctx.memory(this);
    }

    @Override
    public int getIntValue(Event e, Encoder ctx, Model model){
        return Integer.parseInt(model.getConstInterp(toZ3Int(ctx)).toString());
    }
    
    public boolean hasConstValue() {
    	return constValue != null;
    }
    
    public Integer getConstValue() {
    	return constValue;
    }
    
    public void setConstValue(Integer value) {
    	this.constValue = value;
    }
}
