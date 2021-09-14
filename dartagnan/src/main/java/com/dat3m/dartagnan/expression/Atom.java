package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.program.memory.Location;
import com.google.common.collect.ImmutableSet;

import java.math.BigInteger;

import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.SolverContext;

import com.dat3m.dartagnan.expression.op.COpBin;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public final class Atom implements ExprInterface {
	
	private final ExprInterface lhs;
	private final ExprInterface rhs;
	private final COpBin op;

	private Atom(ExprInterface lhs, COpBin op, ExprInterface rhs) {
		this.lhs = lhs;
		this.rhs = rhs;
		this.op = op;
	}

	/**
	 * Builds a binary predicate.
	 * @param o Operator.
	 * @param a First operand.
	 * @param b Second and last operand.
	 * @return Simplified equivalent of the described expression.
	 */
	public static ExprInterface of(COpBin o, ExprInterface a, ExprInterface b) {
		var aa = a.toConstant();
		var bb = b.toConstant();
		if(null==aa || null==bb)
			return new Atom(a, o, b);
		return new IConst(o.combine(aa, bb) ? BigInteger.ONE : BigInteger.ZERO, a.getPrecision());
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
		return op.encode(lhs.toIntFormula(e, ctx), rhs.toIntFormula(e, ctx), ctx);
	}

	@Override
	public Formula getLastValueExpr(SolverContext ctx){
		boolean bp = getPrecision() > 0;
		FormulaManager fmgr = ctx.getFormulaManager();

		Formula tbranch = bp? 
				fmgr.getBitvectorFormulaManager().makeBitvector(getPrecision(), BigInteger.ONE) : 
				fmgr.getIntegerFormulaManager().makeNumber(BigInteger.ONE);
		
		Formula fbranch = bp? 
				fmgr.getBitvectorFormulaManager().makeBitvector(getPrecision(), BigInteger.ZERO) : 
				fmgr.getIntegerFormulaManager().makeNumber(BigInteger.ZERO);
				
		return fmgr.getBooleanFormulaManager().ifThenElse(op.encode(lhs.getLastValueExpr(ctx), rhs.getLastValueExpr(ctx), ctx), 
				tbranch, 
				fbranch);
	}

    @Override
	public ImmutableSet<Register> getRegs() {
		return new ImmutableSet.Builder<Register>().addAll(lhs.getRegs()).addAll(rhs.getRegs()).build();
	}

	@Override
	public ImmutableSet<Location> getLocs() {
		return new ImmutableSet.Builder<Location>().addAll(lhs.getLocs()).addAll(rhs.getLocs()).build();
	}

    @Override
    public String toString() {
        return lhs + " " + op + " " + rhs;
    }
    
    @Override
	public boolean getBoolValue(Event e, Model model, SolverContext ctx){
		return op.combine(lhs.getIntValue(e, model, ctx), rhs.getIntValue(e, model, ctx));
	}
    
    public COpBin getOp() {
    	return op;
    }
    
    public ExprInterface getLHS() {
    	return lhs;
    }
    
    public ExprInterface getRHS() {
    	return rhs;
    }

	@Override
	public int getPrecision() {
		if(lhs.getPrecision() != rhs.getPrecision()) {
            throw new RuntimeException("The type of " + lhs + " and " + rhs + " does not match");
		}
		return lhs.getPrecision();
	}

	@Override
	public int hashCode() {
		return op.hashCode() * lhs.hashCode() + rhs.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != getClass())
			return false;
		Atom expr = (Atom) obj;
		return expr.op == op && expr.lhs.equals(lhs) && expr.rhs.equals(rhs);
	}
}