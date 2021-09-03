package com.dat3m.dartagnan.program.memory;

import java.util.ArrayList;

/**
 * There is a well-defined set of arrays in each bounded program.
 */
final class Array {

	final ArrayList<Address> address;

	Array(int size) {
		address = new ArrayList<>(size);
	}
}
