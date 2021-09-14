package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.program.memory.Location;
import com.google.common.collect.ImmutableSet;

import java.math.BigInteger;

import org.sosy_lab.java_smt.api.BitvectorFormula;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.SolverContext;

import com.dat3m.dartagnan.expression.op.BOpUn;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public final class BExprUn implements ExprInterface {

    private final ExprInterface b;
    private final BOpUn op;

	private BExprUn(BOpUn op, ExprInterface b) {
        this.b = b;
        this.op = op;
    }

	/**
	 * Builds a unary operation.
	 * @param o Operator.
	 * @param a First and only operand.
	 * @return Simplified equivalent.
	 */
	public static ExprInterface of(BOpUn o, ExprInterface a) {
		if(BOpUn.NOT.equals(o)) {
			if(a.isTrue())
				return IConst.ZERO;
			if(a.isFalse())
				return IConst.ONE;
			if(a instanceof BExprUn && BOpUn.NOT.equals(((BExprUn)a).op))
				return ((BExprUn)a).b;
		}
		return new BExprUn(o, a);
	}

    public BOpUn getOp() {
        return op;
    }

    public ExprInterface getInner() {
        return b;
    }

	@Override
	public Formula toIntFormula(Event e, SolverContext ctx) {
		var f = ctx.getFormulaManager();
		var i = f.getIntegerFormulaManager();
		return f.getBooleanFormulaManager().ifThenElse(toBoolFormula(e, ctx),
			i.makeNumber(BigInteger.ONE),
			i.makeNumber(BigInteger.ZERO));
	}

	@Override
	public BigInteger getIntValue(Event e, Model model, SolverContext ctx){
		return getBoolValue(e, model, ctx) ? BigInteger.ONE : BigInteger.ZERO;
	}

    @Override
    public BooleanFormula toBoolFormula(Event e, SolverContext ctx) {
        return op.encode(b.toBoolFormula(e, ctx), ctx);
    }

    @Override
    public Formula getLastValueExpr(SolverContext ctx){
    	FormulaManager fmgr = ctx.getFormulaManager();
		
		BooleanFormula expr = b.getLastValueExpr(ctx) instanceof BitvectorFormula ? 
				fmgr.getBitvectorFormulaManager().greaterThan((BitvectorFormula)b.getLastValueExpr(ctx), fmgr.getBitvectorFormulaManager().makeBitvector(b.getPrecision(), (BigInteger.ONE)), false):
				fmgr.getIntegerFormulaManager().greaterThan((IntegerFormula)b.getLastValueExpr(ctx), fmgr.getIntegerFormulaManager().makeNumber(BigInteger.ONE));
        
		return fmgr.getBooleanFormulaManager().ifThenElse(op.encode(expr, ctx), 
				fmgr.getIntegerFormulaManager().makeNumber(BigInteger.ONE), 
				fmgr.getIntegerFormulaManager().makeNumber(BigInteger.ZERO));
    }

    @Override
    public ImmutableSet<Register> getRegs() {
        return b.getRegs();
    }

    @Override
    public ImmutableSet<Location> getLocs() {
        return b.getLocs();
    }

    @Override
    public String toString() {
        return "(" + op + " " + b + ")";
    }

    @Override
    public boolean getBoolValue(Event e, Model model, SolverContext ctx){
        return op.combine(b.getBoolValue(e, model, ctx));
    }

	@Override
    public int hashCode() {
        return op.hashCode() ^ b.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass())
            return false;
        BExprUn expr = (BExprUn) obj;
        return expr.op == op && expr.b.equals(b);
    }
}
