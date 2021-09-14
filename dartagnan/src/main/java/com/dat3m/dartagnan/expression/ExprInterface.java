package com.dat3m.dartagnan.expression;

import java.math.BigInteger;

import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.SolverContext;

import com.dat3m.dartagnan.program.memory.Location;
import com.google.common.collect.ImmutableSet;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public interface ExprInterface {

	Formula toIntFormula(Event e, SolverContext ctx);

	default BooleanFormula toBoolFormula(Event e, SolverContext ctx) {
		var f = ctx.getFormulaManager();
		var r = toIntFormula(e, ctx);
		if(r instanceof BitvectorFormula) {
			var b = f.getBitvectorFormulaManager();
			return b.greaterThan((BitvectorFormula)r, b.makeBitvector(getPrecision(),BigInteger.ZERO),false);
		}
		assert r instanceof IntegerFormula;
		var i = f.getIntegerFormulaManager();
		return i.greaterThan((IntegerFormula)r, i.makeNumber(BigInteger.ZERO));
	}

	Formula getLastValueExpr(SolverContext ctx);

	BigInteger getIntValue(Event e, Model model, SolverContext ctx);

	default boolean getBoolValue(Event e, Model model, SolverContext ctx) {
		return 1 == getIntValue(e,model,ctx).signum();
	}

	ImmutableSet<Register> getRegs();

	default ImmutableSet<Location> getLocs() {
		return ImmutableSet.of();
	}

	/**
	 * Checks syntactically if this expression always results in {@literal true} if coerced to boolean.
	 */
	default boolean isTrue() {
		return false;
	}

	/**
	 * Checks syntactically if this expression always results in {@literal false} if coerced to boolean.
	 */
	default boolean isFalse() {
		return false;
	}

	/**
	 * Checks syntactically if the value of this expression is static.
	 * In terms of this method, addresses are never static.
	 * @return Value to be used in constant value propagation.
	 * {@code null} if this expression is not a static constant.
	 */
	default BigInteger toConstant() {
		return null;
	}

	default int getPrecision() {
		throw new UnsupportedOperationException("getPrecision() not supported for " + this);
	}

	default ExprInterface getBase() {
		return this;
	}

}
