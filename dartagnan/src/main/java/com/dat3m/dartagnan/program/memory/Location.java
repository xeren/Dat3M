package com.dat3m.dartagnan.program.memory;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Location {

	public static final BigInteger DEFAULT_INIT_VALUE = BigInteger.ZERO;

	private final String name;
	private final List<Address> address;

	public Location(String n, int s, int p) {
		name = n;
		this.address = IntStream.range(0,s).mapToObj(i->new Address(this,i,p)).collect(Collectors.toUnmodifiableList());
	}

	public String getName() {
		return name;
	}

	public List<Address> getAddress() {
		return address;
	}

	@Override
	public String toString() {
		return name;
	}
}
