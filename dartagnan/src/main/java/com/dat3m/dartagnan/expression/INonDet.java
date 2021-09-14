package com.dat3m.dartagnan.expression;

import java.math.BigInteger;

import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.SolverContext;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.google.common.collect.ImmutableSet;

/**
 * Global variable of an associated program.
 * TODO reduce to constant when unrolling if assigned to a value
 */
public class INonDet implements ExprInterface {

	private final BigInteger min;
	private final BigInteger max;
	private BigInteger value;
	private final int precision;

	/**
	 * @param min
	 * Nullable inclusive lower bound of allowed values for this variable.
	 * @param max
	 * Nullable inclusive upper bound of allowed values for this variable.
	 * @param p
	 * Bit precision for the variable.
	 */
	public INonDet(BigInteger min, BigInteger max, int p) {
		this.min = min;
		this.max = max;
		precision = p;
	}

	/**
	 * Callable from a parser, if this variable is equalized to some deterministic value.
	 * Associates this variable with that value.
	 * @param v
	 * Equalized to this.
	 * @throws IllegalStateException
	 * Already assigned to a different value.
	 */
	public void assign(BigInteger v) {
		if(null!=value && !value.equals(v))
			throw new IllegalStateException("constant assigned to both "+value+" and "+v);
		value = v;
	}

	@Override
	public Formula toIntFormula(Event e, SolverContext ctx) {
		return getLastValueExpr(ctx);
	}

	@Override
	public Formula getLastValueExpr(SolverContext ctx) {
		var m = ctx.getFormulaManager();
		if(0<precision) {
			var b = m.getBitvectorFormulaManager();
			if(null!=value)
				return b.makeBitvector(precision, value);
			return b.makeVariable(precision, Integer.toString(hashCode()));
		}
		else {
			var i = m.getIntegerFormulaManager();
			if(null!=value)
				return i.makeNumber(value);
			return i.makeVariable(Integer.toString(hashCode()));
		}
	}

	@Override
	public BigInteger getIntValue(Event e, Model model, SolverContext ctx) {
		if(null!=value)
			return value;
		Object value = model.evaluate(toIntFormula(e, ctx));
		if(value != null) {
			return new BigInteger(value.toString());			
		}
        throw new RuntimeException("No value in the model for " + this);
	}

	@Override
	public ImmutableSet<Register> getRegs() {
		return ImmutableSet.of();
	}

	@Override
	public int getPrecision() {
		return precision;
	}
	
	public BooleanFormula encodeBounds(boolean bp, SolverContext ctx) {
		var fmgr = ctx.getFormulaManager();
		var bmgr = fmgr.getBooleanFormulaManager();
		var enc = bmgr.makeTrue();
		if(null!=value)
			return enc;
		if(bp) {
			boolean signed = !BigInteger.ZERO.equals(min);
			var bvmgr = fmgr.getBitvectorFormulaManager();
			if(null!=min && signed)
				enc = bmgr.and(enc, bvmgr.greaterOrEquals((BitvectorFormula) toIntFormula(null,ctx), bvmgr.makeBitvector(precision, min), true));
			if(null!=max)
				enc = bmgr.and(enc, bvmgr.lessOrEquals((BitvectorFormula) toIntFormula(null,ctx), bvmgr.makeBitvector(precision, max), signed));
		} else {
			var imgr = fmgr.getIntegerFormulaManager();
			if(null!=min)
				enc = bmgr.and(enc, imgr.greaterOrEquals((IntegerFormula) toIntFormula(null,ctx), imgr.makeNumber(min)));
			if(null!=max)
				enc = bmgr.and(enc, imgr.lessOrEquals((IntegerFormula) toIntFormula(null,ctx), imgr.makeNumber(max)));
		}
		return enc;
	}

	@Override
	public String toString() {
		return "nondet("+(null!=min ? min.toString() : "")+","+(null!=max ? max.toString() : "")+")";
	}
}
