package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.expression.op.IOpUn;
import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.program.memory.Location;
import com.google.common.collect.ImmutableSet;

import java.math.BigInteger;

import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.SolverContext;

import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class IExprBin implements ExprInterface {

    private final ExprInterface lhs;
    private final ExprInterface rhs;
    private final IOpBin op;

	private IExprBin(ExprInterface lhs, IOpBin op, ExprInterface rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.op = op;
    }

	/**
	 * Builds a binary term.
	 * @param o Operator.
	 * @param a First operand.
	 * @param b Second and last operand.
	 * @return Simplified equivalent of the described term.
	 */
	public static ExprInterface of(IOpBin o, ExprInterface a, ExprInterface b) {
		var aa = a.toConstant();
		var bb = b.toConstant();
		if(null!=aa && null!=bb)
			return new IConst(o.combine(aa, bb), a.getPrecision());
		if(null==aa && null==bb)
			return new IExprBin(a, o, b);
		var c = null!=aa ? aa : bb;
		var x = null!=aa ? b : a;
		switch(o) {
		case PLUS:
			if(BigInteger.ZERO.equals(c))
				return x;
			if(x instanceof IConst)
				//assert x instanceof Address
				return ((IConst)x).add(c);
		case MINUS:
			if(BigInteger.ZERO.equals(aa))
				return new IExprUn(IOpUn.MINUS, b);
			if(BigInteger.ZERO.equals(bb))
				return a;
		case MULT:
			if(BigInteger.ZERO.equals(aa))
				return a;
			if(BigInteger.ZERO.equals(bb))
				return b;
			if(BigInteger.ONE.equals(c))
				return x;
		}
		//commutation
		switch(o) {
		case PLUS:
		case MULT:
		case AND:
		case OR:
		case XOR:
			if(x instanceof IExprBin && o.equals(((IExprBin)x).op)) {
				var cc = ((IExprBin)x).lhs.toConstant();
				var dd = ((IExprBin)x).rhs.toConstant();
				assert null==cc || null==dd;
				if(null!=cc)
					return new IExprBin(((IExprBin)x).rhs, o, new IConst(o.combine(cc, c), a.getPrecision()));
				if(null!=dd)
					return new IExprBin(((IExprBin)x).lhs, o, new IConst(o.combine(dd, c), a.getPrecision()));
			}
		}
		return new IExprBin(a, o, b);
	}

    @Override
    public Formula toIntFormula(Event e, SolverContext ctx) {
        return op.encode(lhs.toIntFormula(e, ctx), rhs.toIntFormula(e, ctx), ctx);
    }

    @Override
    public Formula getLastValueExpr(SolverContext ctx){
        return op.encode(lhs.getLastValueExpr(ctx), rhs.getLastValueExpr(ctx), ctx);
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
        return "(" + lhs + " " + op + " " + rhs + ")";
    }

    @Override
    public BigInteger getIntValue(Event e, Model model, SolverContext ctx){
        return op.combine(lhs.getIntValue(e, model, ctx), rhs.getIntValue(e, model, ctx));
    }
    
    @Override
	public IConst reduce() {
    	BigInteger v1 = lhs.reduce().getIntValue();
    	BigInteger v2 = rhs.reduce().getIntValue();
		return new IConst(op.combine(v1, v2), lhs.getPrecision());
	}

	@Override
	public int getPrecision() {
		if(lhs.getPrecision() != rhs.getPrecision()) {
            throw new RuntimeException("The type of " + lhs + " and " + rhs + " does not match");
		}
		return lhs.getPrecision();
	}
	
	@Override
	public ExprInterface getBase() {
		return lhs.getBase();
	}
	
	public IOpBin getOp() {
		return op;
	}
	
	public ExprInterface getRHS() {
		return rhs;
	}

	public ExprInterface getLHS() {
		return lhs;
	}

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return (op.combine(BigInteger.valueOf(lhs.hashCode()), BigInteger.valueOf(rhs.hashCode()))).intValue();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass())
            return false;
        IExprBin expr = (IExprBin) obj;
        return expr.op == op && expr.lhs.equals(lhs) && expr.rhs.equals(rhs);
    }
}
