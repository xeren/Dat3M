package com.dat3m.dartagnan.program.memory;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.java_smt.api.*;

import static java.util.Objects.requireNonNull;
import static org.sosy_lab.java_smt.api.FormulaType.IntegerType;
import static org.sosy_lab.java_smt.api.FormulaType.getBitvectorTypeWithSize;

import java.math.BigInteger;

public class Address extends IConst implements ExprInterface {

	final Array array;

	Address(Array array, int index){
		super(BigInteger.valueOf(index), array.precision);
		this.array = array;
	}

	public void setConstantValue(BigInteger value) {
		array.constant = value.subtract(this.value);
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
		var m = ctx.getFormulaManager();
		var name = "last_val_at_memory_" + array.name + "[" + value + "]";
		var type = precision > 0 ? getBitvectorTypeWithSize(precision) : IntegerType;
		return m.makeVariable(type, name);
	}

    @Override
    public BooleanFormula toBoolFormula(Event e, SolverContext ctx){
        return ctx.getFormulaManager().getBooleanFormulaManager().makeTrue();
    }

	@Override
	public Formula toIntFormula(SolverContext ctx){
		var m = ctx.getFormulaManager();
		if(array.constant != null) {
			var c = array.constant.add(value);
			return precision > 0
					? m.getBitvectorFormulaManager().makeBitvector(precision, c)
					: m.getIntegerFormulaManager().makeNumber(c);
		}
		var i = m.getIntegerFormulaManager();
		var v = array.toVariable(i);
		return BigInteger.ZERO.equals(value) ? v : i.add(v, i.makeNumber(value));
	}

	@Override
	public BigInteger getIntValue(Event e, Model model, SolverContext ctx){
		return value.add(array.constant!=null ? array.constant : new BigInteger(requireNonNull(model.evaluate(array.toVariable(ctx.getFormulaManager().getIntegerFormulaManager()))).toString()));
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
