package com.dat3m.dartagnan.program.memory;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.java_smt.api.*;

import static org.sosy_lab.java_smt.api.FormulaType.IntegerType;
import static org.sosy_lab.java_smt.api.FormulaType.getBitvectorTypeWithSize;

import java.math.BigInteger;

public class Address extends IConst implements ExprInterface {

	final Array array;
    private BigInteger constantValue;

	Address(Array array, int index, int precision){
        super(BigInteger.valueOf(index), precision);
		this.array = array;
    }

    public boolean hasConstantValue() {
     	return this.constantValue != null;
     }

    public BigInteger getConstantValue() {
     	return this.constantValue;
     }

    public void setConstantValue(BigInteger value) {
     	this.constantValue = value;
     }

    @Override
    public ImmutableSet<Register> getRegs(){
        return ImmutableSet.of();
    }

    @Override
    public Formula toIntFormula(Event e, SolverContext ctx){
        return toIntFormula(ctx);
    }

    @Override
    public Formula getLastValueExpr(SolverContext ctx){
        return toIntFormula(ctx);
    }

    public Formula getLastMemValueExpr(SolverContext ctx){
        FormulaManager fmgr = ctx.getFormulaManager();
		String name = "last_val_at_memory_" + array.name + "[" + value + "]";
		FormulaType<?> type = precision > 0 ? getBitvectorTypeWithSize(precision) : IntegerType;
		return fmgr.makeVariable(type, name);
    }

    @Override
    public BooleanFormula toBoolFormula(Event e, SolverContext ctx){
        return ctx.getFormulaManager().getBooleanFormulaManager().makeTrue();
    }

    @Override
    public Formula toIntFormula(SolverContext ctx){
		FormulaManager fmgr = ctx.getFormulaManager();
		if(constantValue != null) {
			return precision > 0 ? 
					fmgr.getBitvectorFormulaManager().makeBitvector(precision, constantValue) : 
					fmgr.getIntegerFormulaManager().makeNumber(constantValue);
    	}
		String name = "memory_" + array.name + "[" + value + "]";
		FormulaType<?> type = precision > 0 ? getBitvectorTypeWithSize(precision) : IntegerType;
		return fmgr.makeVariable(type, name);
    }

    @Override
    public BigInteger getIntValue(Event e, Model model, SolverContext ctx){
        if (hasConstantValue()) {
            return constantValue;
        }
        return new BigInteger(model.evaluate(toIntFormula(ctx)).toString());
    }

	@Override
	public boolean isTrue() {
		return true;
	}

	@Override
	public boolean isFalse() {
		return false;
	}

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }

	@Override
	public String toString() {
		return "&mem" + array.name + "[" + value + "]";
	}

	@Override
	public int hashCode() {
		return array.hashCode() ^ value.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Address && array.equals(((Address)o).array) && value.equals(((Address)o).value);
	}
}
