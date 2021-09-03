package com.dat3m.dartagnan.program.memory;

import java.util.ArrayList;

/**
 * There is a well-defined set of arrays in each bounded program.
 */
final class Array {

	final int name;
	final ArrayList<Address> address;
	final boolean isStatic;

	Array(int name, int size, int precision, boolean isStatic) {
		this.name = name;
		address = new ArrayList<>(size);
		this.isStatic = isStatic;
		for(int i = 0; i < size; ++i)
			address.add(new Address(this, i, precision));
	}
}
