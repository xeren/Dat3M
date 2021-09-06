package com.dat3m.dartagnan.program.memory;

import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * There is a well-defined set of arrays in each bounded program.
 */
final class Array {

	final int name;
	final ArrayList<Address> address;
	final int precision;
	final boolean isStatic;
	BigInteger constant;

	Array(int name, int size, int precision, boolean isStatic) {
		this.name = name;
		address = new ArrayList<>(size);
		this.precision = precision;
		this.isStatic = isStatic;
		for(int i = 0; i < size; ++i)
			address.add(new Address(this, i));
	}

	IntegerFormula toVariable(IntegerFormulaManager m) {
		assert null==constant;
		return m.makeVariable("memory_"+name);
	}
}
