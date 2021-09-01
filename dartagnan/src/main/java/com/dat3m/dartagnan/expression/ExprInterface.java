package com.dat3m.dartagnan.expression;

import java.math.BigInteger;

import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.SolverContext;

import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.program.memory.Location;
import com.google.common.collect.ImmutableSet;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public interface ExprInterface {

	Formula toIntFormula(Event e, SolverContext ctx);

	BooleanFormula toBoolFormula(Event e, SolverContext ctx);

	Formula getLastValueExpr(SolverContext ctx);

	BigInteger getIntValue(Event e, Model model, SolverContext ctx);

	boolean getBoolValue(Event e, Model model, SolverContext ctx);

	ImmutableSet<Register> getRegs();

	default ImmutableSet<Location> getLocs() {
		return ImmutableSet.of();
	}

	default IConst reduce() {
		throw new UnsupportedOperationException("Reduce not supported for " + this);
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

	default int getPrecision() {
		throw new UnsupportedOperationException("getPrecision() not supported for " + this);
	}

	default IExpr getBase() {
		throw new UnsupportedOperationException("getBase not supported for " + this);
	}

	<T> T visit(ExpressionVisitor<T> visitor);

	//default ExprInterface simplify() { return visit(ExprSimplifier.SIMPLIFIER); }

}
