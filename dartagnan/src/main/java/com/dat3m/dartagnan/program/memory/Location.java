package com.dat3m.dartagnan.program.memory;

import java.math.BigInteger;

public class Location {

	public static final BigInteger DEFAULT_INIT_VALUE = BigInteger.ZERO;

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
}
