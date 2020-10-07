package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.memory.Address;
import com.microsoft.z3.IntExpr;
import java.util.Set;
import static java.util.Collections.disjoint;

public abstract class MemEvent extends Event {

	protected final IExpr address;
	protected final String mo;
	private Set<Address> maxAddressSet;

	public MemEvent(IExpr address, String mo) {
		this.address = address;
		this.mo = mo;
		if(mo != null) {
			addFilters(mo);
		}
	}

	protected MemEvent(MemEvent other) {
		super(other);
		this.address = other.address;
		this.maxAddressSet = other.maxAddressSet;
		this.mo = other.mo;
	}

	public IntExpr getMemValueExpr(EncodeContext context) {
		throw new RuntimeException("Attempt to access not initialised value expression in " + this);
	}

	public Set<Address> getMaxAddressSet() {
		if(maxAddressSet != null) {
			return maxAddressSet;
		}
		throw new RuntimeException("Location set has not been initialised for memory event " + this);
	}

	public void setMaxAddressSet(Set<Address> maxAddressSet) {
		this.maxAddressSet = maxAddressSet;
	}

	public IExpr getAddress() {
		return address;
	}

	public ExprInterface getMemValue() {
		throw new RuntimeException("MemValue is not available for event " + this.getClass().getName());
	}

	public static boolean canAddressTheSameLocation(MemEvent e1, MemEvent e2) {
		return !disjoint(e1.getMaxAddressSet(), e2.getMaxAddressSet());
	}
}