package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.program.event.Event;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.java_smt.api.*;

import java.math.BigInteger;

import static org.sosy_lab.java_smt.api.FormulaType.IntegerType;
import static org.sosy_lab.java_smt.api.FormulaType.getBitvectorTypeWithSize;

public class Register implements ExprInterface {

	private static int dummyCount = 0;

	private final Thread thread;
	private final String name;
	private String cVar;

    private final int precision;

	/**
	 * @param t
	 * Scope for this register.
	 * @param n
	 * Identifies this register in {@code t}.
	 * @param p
	 * Bit precision for stored values.
	 */
	public Register(Thread t, String n, int p) {
		thread = t;
		name = n;
		precision = p;
	}

	/**
	 * Dummy registers with an unspecified name.
	 * @param t
	 * Scope for this register.
	 * @param p
	 * Bit precision for stored values.
	 */
	public Register(Thread t, int p) {
		this(t, "DUMMY_REG_"+dummyCount++, p);
	}

	public Thread getThread() {
		return thread;
	}

	public String getName() {
		return name;
	}

	public String getCVar() {
		return cVar;
	}

	public void setCVar(String name) {
		this.cVar = name;
	}

	public int getThreadId(){
		return thread.getId();
	}

	@Override
	public Formula toIntFormula(Event e, SolverContext ctx) {
		String name = getName() + "(" + e.repr() + ")";
		FormulaManager fmgr = ctx.getFormulaManager();
		FormulaType<?> type = precision > 0 ? getBitvectorTypeWithSize(precision) : IntegerType;
		return fmgr.makeVariable(type, name);
	}

	public Formula toIntFormulaResult(Event e, SolverContext ctx) {
		String name = getName() + "(" + e.repr() + "_result)";
		FormulaManager fmgr = ctx.getFormulaManager();
		FormulaType<?> type = precision > 0 ? getBitvectorTypeWithSize(precision) : IntegerType;
		return fmgr.makeVariable(type, name);
	}

	@Override
	public ImmutableSet<Register> getRegs() {
		return ImmutableSet.of(this);
	}

	@Override
	public Formula getLastValueExpr(SolverContext ctx){
		String name = getName() + "_" + thread.getId() + "_final";
		FormulaManager fmgr = ctx.getFormulaManager();
		FormulaType<?> type = precision > 0 ? getBitvectorTypeWithSize(precision) : IntegerType;
		return fmgr.makeVariable(type, name);
	}

	@Override
	public BigInteger getIntValue(Event e, Model model, SolverContext ctx){
		return new BigInteger(model.evaluate(toIntFormula(e, ctx)).toString());
	}

	@Override
	public int getPrecision() {
    	return precision;
    }

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode() {
		return (name.hashCode() << 8) + thread.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return this==o || o instanceof Register && name.equals(((Register)o).name) && thread.equals(((Register)o).thread);
	}
}
