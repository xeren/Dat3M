package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.expression.processing.ExpressionVisitor;
import com.dat3m.dartagnan.program.memory.Location;
import com.google.common.collect.ImmutableSet;

import java.math.BigInteger;

import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.Model;
import org.sosy_lab.java_smt.api.SolverContext;

import com.dat3m.dartagnan.expression.op.IOpUn;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;

public class IExprUn implements ExprInterface {

	private final ExprInterface b;
	private final IOpUn op;

    public IExprUn(IOpUn op, ExprInterface b) {
        this.b = b;
        this.op = op;
    }

	public IOpUn getOp() {
		return op;
	}

	public ExprInterface getInner() {
		return b;
	}

	@Override
	public Formula toIntFormula(Event e, SolverContext ctx) {
		return op.encode(b.toIntFormula(e, ctx), ctx);
	}

	@Override
	public Formula getLastValueExpr(SolverContext ctx) {
        return op.encode(b.getLastValueExpr(ctx), ctx);
	}

	@Override
	public BigInteger getIntValue(Event e, Model model, SolverContext ctx) {
        return b.getIntValue(e, model, ctx).negate();
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
        return "(" + op + b + ")";
    }

	@Override
	public int getPrecision() {
		return b.getPrecision();
	}

	@Override
	public <T> T visit(ExpressionVisitor<T> visitor) {
		return visitor.visit(this);
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
		IExprUn expr = (IExprUn) obj;
		return expr.op == op && expr.b.equals(b);
	}
}
