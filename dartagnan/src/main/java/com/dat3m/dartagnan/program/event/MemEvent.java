package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.utils.EType;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import java.util.HashSet;

public abstract class MemEvent extends Event {

	protected final IExpr address;
	protected Expr memAddressExpr;
	protected Expr memValueExpr;
	private ImmutableSet<Address> maxAddressSet;

	public MemEvent(IExpr address) {
		this.address = address;
	}

	protected MemEvent(MemEvent other) {
		super(other);
		this.address = other.address;
		this.maxAddressSet = other.maxAddressSet;
		this.memAddressExpr = other.memAddressExpr;
		this.memValueExpr = other.memValueExpr;
	}

	public Expr getMemAddressExpr() {
		if(memAddressExpr != null) {
			return memAddressExpr;
		}
		throw new RuntimeException("Attempt to access not initialised address expression in " + this);
	}

	public Expr getMemValueExpr() {
		if(memValueExpr != null) {
			return memValueExpr;
		}
		throw new RuntimeException("Attempt to access not initialised value expression in " + this);
	}

	public ImmutableSet<Address> getMaxAddressSet() {
		if(maxAddressSet != null) {
			return maxAddressSet;
		}
		throw new RuntimeException("Location set has not been initialised for memory event " + this);
	}

	public void setMaxAddressSet(ImmutableSet<Address> maxAddressSet) {
		this.maxAddressSet = maxAddressSet;
	}

	public IExpr getAddress() {
		return address;
	}

	public ExprInterface getMemValue() {
		throw new RuntimeException("MemValue is not available for event " + this.getClass().getName());
	}

	public static boolean canAddressTheSameLocation(MemEvent e1, MemEvent e2) {
		return !Sets.intersection(e1.getMaxAddressSet(), e2.getMaxAddressSet()).isEmpty();
	}

	public boolean canRace() {
		//TODO associate tags with data race inability
		HashSet<String> f = new HashSet<>(filter);
		f.remove(EType.ANY);
		f.remove(EType.MEMORY);
		f.remove(EType.RMW);
		f.remove(EType.READ);
		f.remove(EType.WRITE);
		f.remove(EType.INIT);
		f.remove(EType.EXCLUSIVE);
		return f.isEmpty();
	}

	@Override
	public void encode(Context c, RuleAcceptor out, BoolExpr in) {
		super.encode(c, out, in);
		memAddressExpr = address.toZ3Int(this, c);
	}
}