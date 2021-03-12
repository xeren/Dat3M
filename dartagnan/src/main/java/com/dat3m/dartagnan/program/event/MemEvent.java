package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.program.ControlBlock;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.memory.Address;

public abstract class MemEvent extends Event {

    protected final IExpr address;
    protected final String mo;

    protected Expr memAddressExpr;
    protected Expr memValueExpr;
    private ImmutableSet<Address> maxAddressSet;

    public MemEvent(IExpr address, String mo){
        this.address = address;
        this.mo = mo;
        if(mo != null){
            addFilters(mo);
        }
    }

    protected MemEvent(MemEvent other){
        super(other);
        this.address = other.address;
        this.maxAddressSet = other.maxAddressSet;
        this.memAddressExpr = other.memAddressExpr;
        this.memValueExpr = other.memValueExpr;
        this.mo = other.mo;
    }

	@Override
	public ControlBlock initialise(Context c, ControlBlock b, ControlMessage m) {
		memAddressExpr = address.toZ3Int(this,c);
		return super.initialise(c,b,m);
	}

    public Expr getMemAddressExpr(){
        if(memAddressExpr != null){
            return memAddressExpr;
        }
        throw new RuntimeException("Attempt to access not initialised address expression in " + this);
    }

    public Expr getMemValueExpr(){
        if(memValueExpr != null){
            return memValueExpr;
        }
        throw new RuntimeException("Attempt to access not initialised value expression in " + this);
    }

    public ImmutableSet<Address> getMaxAddressSet(){
        if(maxAddressSet != null){
            return maxAddressSet;
        }
        throw new RuntimeException("Location set has not been initialised for memory event " + this);
    }

    public void setMaxAddressSet(ImmutableSet<Address> maxAddressSet){
        this.maxAddressSet = maxAddressSet;
    }

    public IExpr getAddress(){
        return address;
    }

    public ExprInterface getMemValue(){
        throw new RuntimeException("MemValue is not available for event " + this.getClass().getName());
    }

    public static boolean canAddressTheSameLocation(MemEvent e1, MemEvent e2){
        return !Sets.intersection(e1.getMaxAddressSet(), e2.getMaxAddressSet()).isEmpty();
    }
    
    public boolean canRace() {
    	return mo == null || mo == "NA";
    }
}