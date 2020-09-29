package com.dat3m.dartagnan.program.memory;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

public class Location implements ExprInterface {

	public static final int DEFAULT_INIT_VALUE = 0;

	private final String name;
	private final Address address;

	public Location(String name, Address address) {
		this.name = name;
		this.address = address;
	}
	
	public String getName() {
		return name;
	}

	public Address getAddress() {
		return address;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode(){
		return address.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;

		if (obj == null || getClass() != obj.getClass())
			return false;

		return address.hashCode() == obj.hashCode();
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c){
		return address.getLastMemValueExpr(c);
	}

	@Override
	public IntExpr toZ3Int(Event e, EncodeContext c){
		if(e instanceof MemEvent){
			return ((MemEvent) e).getMemValueExpr();
		}
		throw new RuntimeException("Attempt to encode memory value for illegal event");
	}

	@Override
	public BoolExpr toZ3Bool(Event e, EncodeContext c){
		if(e instanceof MemEvent){
			return c.lt(c.zero(), ((MemEvent) e).getMemValueExpr());
		}
		throw new RuntimeException("Attempt to encode memory value for illegal event");
	}

	@Override
	public int getIntValue(Event e, EncodeContext c, Model m){
		if(e instanceof MemEvent){
			return ((MemEvent) e).getMemValue().getIntValue(e, c, m);
		}
		throw new RuntimeException("Attempt to encode memory value for illegal event");
	}

	@Override
	public boolean getBoolValue(Event e, EncodeContext c, Model m){
		if(e instanceof MemEvent){
			return ((MemEvent) e).getMemValue().getBoolValue(e, c, m);
		}
		throw new RuntimeException("Attempt to encode memory value for illegal event");
	}

	@Override
	public IConst reduce() {
		throw new UnsupportedOperationException("Reduce not supported for " + this);
	}
}
