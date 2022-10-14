package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.core.Event;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.java_smt.api.*;

import java.math.BigInteger;

public interface ExprInterface {

    Formula toIntFormula(Event event, FormulaManager formulaManager);

    BooleanFormula toBoolFormula(Event event, FormulaManager formulaManager);

    BigInteger getIntValue(Event event, Model model, FormulaManager formulaManager);

    boolean getBoolValue(Event event, Model model, FormulaManager formulaManager);

    default ImmutableSet<Register> getRegs() {
    	return ImmutableSet.of();
    }

    <T> T visit(ExpressionVisitor<T> visitor);

    //default ExprInterface simplify() { return visit(ExprSimplifier.SIMPLIFIER); }
    
}
