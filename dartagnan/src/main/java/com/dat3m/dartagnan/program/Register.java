package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.IExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import java.util.Set;
import static java.util.stream.Stream.concat;
import static java.util.stream.Collectors.toUnmodifiableSet;

public class Register extends IExpr implements ExprInterface {

	private static int dummyCount = 0;

	private final String name;
	private final int threadId;

	public Register(String name, int threadId) {
		if(name == null) {
			name = "DUMMY_REG_" + dummyCount++;
		}
		this.name = name;
		this.threadId = threadId;
	}

	public String getName() {
		return name;
	}

	public int getThreadId() {
		return threadId;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode() {
		return (name.hashCode() << 8) + threadId;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;

		if(obj == null || getClass() != obj.getClass())
			return false;

		Register rObj = (Register) obj;
		return name.equals(rObj.name) && threadId == rObj.threadId;
	}

	@Override
	public IntExpr toZ3Int(Event e, EncodeContext c) {
		return c.context.mkIntConst(getName() + "(" + e.getCId() + ")");
	}

	public IntExpr toZ3IntResult(Event e, EncodeContext c) {
		return c.context.mkIntConst(getName() + "(" + e.getCId() + "_result)");
	}

	/**
	 * @param expression
	 * Some expression in a program.
	 * @return
	 * Immutable set of those registers occurring in {@code expression}.
	 */
	public static Set<Register> of(ExprInterface expression) {
		return expression.stream().filter(Register.class::isInstance).map(Register.class::cast).collect(toUnmodifiableSet());
	}

	public static Set<Register> of(Set<?extends Register> base, ExprInterface expression) {
		return concat(base.stream(), expression.stream().filter(Register.class::isInstance).map(Register.class::cast)).collect(toUnmodifiableSet());
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		return c.context.mkIntConst(getName() + "_" + threadId + "_final");
	}

	@Override
	public int getIntValue(Event e, EncodeContext ctx, Model model) {
		return Integer.parseInt(model.getConstInterp(toZ3Int(e, ctx)).toString());
	}

	@Override
	public IConst reduce() {
		throw new UnsupportedOperationException("Reduce not supported for " + this);
	}
}
