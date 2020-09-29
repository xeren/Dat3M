package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Consists of operations on data a program has to compute.
 * May depend on state information.
 * Implicitly casts integer values to truth values and vice versa:
 * Positive integers are cast to {@code true}, non-positive integers result in {@code false} ({@link IExpr}).
 * {@code true} is cast to {@code 1} and {@code false} is cast to {@code 0} ({@link BExpr}).
 */
public interface ExprInterface {

	IConst reduce();

	IntExpr toZ3Int(Event e, EncodeContext c);

	BoolExpr toZ3Bool(Event e, EncodeContext c);

	IntExpr getLastValueExpr(EncodeContext c);

	int getIntValue(Event e, EncodeContext c, Model m);

	boolean getBoolValue(Event e, EncodeContext c, Model m);

	default void subexpression(Consumer<ExprInterface> action) {
	}

	default Stream<ExprInterface> stream() {
		Stack<ExprInterface> stack = new Stack<>();
		stack.push(this);
		return Stream.iterate(stack, s->!s.empty(), s->{s.pop().subexpression(s::push);return s;}).map(Stack::peek);
	}
}
