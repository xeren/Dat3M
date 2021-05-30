package com.dat3m.dartagnan.program.memory;

import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;

import java.math.BigInteger;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class Address extends IConst implements ExprInterface {

	private final Location location;
	private final int index;
    private BigInteger constantValue;
	private IConst initialValue;

	Address(Location l, int i, int p) {
		super(BigInteger.valueOf(i),p);
		location = l;
		index = i;
    }

	public Location getLocation() {
		return location;
	}

	public void setInitialValue(IConst value) {
		initialValue = value;
	}

	public IConst getInitialValue() {
		return null!=initialValue ? initialValue : new IConst(Location.DEFAULT_INIT_VALUE,precision);
	}

    public boolean hasConstantValue() {
     	return this.constantValue != null;
     }

    public BigInteger getConstantValue() {
     	return this.constantValue;
     }

    public void setConstantValue(BigInteger value) {
     	this.constantValue = value;
     }

	public Address add(int offset) {
		//offset==0 accelerates the most likely case
		return offset==0 ? this : location.get(index+offset);
	}

	public boolean valid() {
		return 0 <= index && index < location.getAddress().size();
	}

	public boolean mayAlias(Address other) {
		if(location.equals(other.location))
			return index == other.index;
		return !valid() || !other.valid();
	}

    @Override
    public ImmutableSet<Register> getRegs(){
        return ImmutableSet.of();
    }

    @Override
    public Expr toZ3Int(Event e, Context ctx){
        return toZ3Int(ctx);
    }

    @Override
    public Expr getLastValueExpr(Context ctx){
        return toZ3Int(ctx);
    }

    public Expr getLastMemValueExpr(Context ctx){
		var n = location.getName()+'['+index+']';
		return precision > 0 ? ctx.mkBVConst(n,precision) : ctx.mkIntConst(n);
    }

    @Override
    public BoolExpr toZ3Bool(Event e, Context ctx){
        return ctx.mkTrue();
    }

    @Override
    public String toString(){
		return "&"+location+'['+index+']';
    }

    @Override
    public int hashCode(){
		return location.hashCode() ^ index;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

		return index==((Address)obj).index && location.equals(((Address)obj).location);
    }

    @Override
    public Expr toZ3Int(Context ctx){
    	if(constantValue != null) {
    		return precision > 0 ? ctx.mkBV(constantValue.toString(), precision) : ctx.mkInt(constantValue.toString());
    	}
		return precision > 0 ? ctx.mkBVConst(toString(),precision) : ctx.mkIntConst(toString());
    }

    @Override
    public BigInteger getIntValue(Event e, Model model, Context ctx){
        return new BigInteger(model.getConstInterp(toZ3Int(ctx)).toString());
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
