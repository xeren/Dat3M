package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.expression.LastValueInterface;
import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.program.event.core.Event;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.java_smt.api.*;

import java.math.BigInteger;

public class Register extends IExpr implements LastValueInterface {

	public static final int NO_THREAD = -1;

	private final String name;
	private String cVar;
    private final int threadId;

    private final int precision;

	public Register(String name, int threadId, int precision) {
		this.name = name;
		this.threadId = threadId;
		this.precision = precision;
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
		return threadId;
	}

	@Override
	public String toString() {
        return name;
	}

    @Override
    public int hashCode(){
        return name.hashCode() + threadId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

        Register rObj = (Register) obj;
        return name.equals(rObj.name) && threadId == rObj.threadId;
    }

	@Override
	public Formula toIntFormula(Event e, FormulaManager m) {
		String name = getName() + "(" + e.repr() + ")";
		return makeVariable(name, m);
	}

	public Formula toIntFormulaResult(Event event, FormulaManager formulaManager) {
		String name = getName() + "(" + event.repr() + "_result)";
		return makeVariable(name, formulaManager);
	}

	@Override
	public ImmutableSet<Register> getRegs() {
		return ImmutableSet.of(this);
	}

	@Override
	public Formula getLastValueExpr(FormulaManager m) {
		String name = getName() + "_" + threadId + "_final";
		return makeVariable(name, m);
	}

	@Override
	public BigInteger getIntValue(Event e, Model model, FormulaManager m) {
		return new BigInteger(model.evaluate(toIntFormula(e, m)).toString());
	}

	@Override
	public <T> T visit(ExpressionVisitor<T> visitor) {
		return visitor.visit(this);
	}

	@Override
	public int getPrecision() {
    	return precision;
    }

	@Override
	public IExpr getBase() {
    	return this;
    }

	private Formula makeVariable(String name, FormulaManager m) {
		FormulaType<?> type = precision > 0 ? FormulaType.getBitvectorTypeWithSize(precision) : FormulaType.IntegerType;
		return m.makeVariable(type, name);
	}
}
