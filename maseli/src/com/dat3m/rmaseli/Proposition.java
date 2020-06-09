package com.dat3m.rmaseli;
import com.microsoft.z3.*;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * Also known as formula or expression of a boolean value.
 * Template for expressions specialized for program states.
 */
public interface Proposition
{

	/**
	 * Constructs a representation in the solver's format.
	 * @param c
	 * Factory for expressions.
	 * @param binding
	 * State of the program.
	 * @return
	 * SMT-Expression.
	 */
	BoolExpr express(Context c, IntFunction<IntExpr> binding);

	/**
	 * Assigns values to all variables using a model.
	 * @param m
	 * Assignment of values to free variables.
	 * @param binding
	 * State to be queried.
	 * @return
	 * This proposition is satisfied by {@code m}.
	 */
	boolean interpret(Model m, IntFunction<IntExpr> binding);

	/**
	 * Those registers used in the proposition.
	 * @param set
	 * Bitset for registers.  Entry is set for each used register.
	 */
	void register(boolean[] set);

	/**
	 * Forms the conjunction of propositions.
	 * @param operand
	 * Sequence of sub formulas.
	 * @return
	 * Proposition that is satisfied iff all of {@code operand} are satisfied.
	 */
	static Proposition and(Proposition... operand)
	{
		return variadic(Context::mkAnd, Stream::allMatch, "and", operand);
	}

	/**
	 * Forms the disjunction of propositions.
	 * @param operand
	 * Sequence of sub formulas.
	 * @return
	 * Proposition that is satisfied iff at least one of {@code operand} is satisfied.
	 */
	static Proposition or(Proposition... operand)
	{
		return variadic(Context::mkOr, Stream::anyMatch, "or", operand);
	}

	/**
	 * Forms the distinction of propositions.
	 * Semantically equivalent to {@code or(and(first, not(second)), and(not(first), second))}.
	 * @param first
	 * Some sub formula.
	 * @param second
	 * Some other sub formula.
	 * @return
	 * Proposition that is satisfied iff exactly one of {@code first} and {@code second} is satisfied.
	 */
	static Proposition xor(Proposition first, Proposition second)
	{
		return binary(Context::mkXor, (a,b)->a!=b, "xor", first, second);
	}

	/**
	 * Semantically equivalent to {@code or(not(premise), conclusion)}.
	 * @param premise
	 * Description of observed cases.
	 * @param conclusion
	 * Specification for observed cases.
	 * @return
	 * Proposition violated iff {@code premise} holds but not {@code conclusion}.
	 */
	static Proposition le(Proposition premise, Proposition conclusion)
	{
		return binary(Context::mkImplies, (a,b)->!a||b, "implies", premise, conclusion);
	}

	/**
	 * Equivalent to {@code or(and(first, second), and(not(first), not(second)))}.
	 * @param first
	 * Some sub formula.
	 * @param second
	 * Some other sub formula.
	 * @return
	 * Proposition satisfied iff both are satisfied or both are violated.
	 */
	static Proposition eq(Proposition first, Proposition second)
	{
		return binary(Context::mkEq, (a,b)->a==b, "eq", first, second);
	}

	/**
	 * Forms the negation of a proposition.
	 * @param operand
	 * Some Proposition.
	 * @return
	 * Proposition satisfied iff {@code operand} is violated.
	 */
	static Proposition not(Proposition operand)
	{
		return new Proposition()
		{
			@Override public BoolExpr express(Context c, IntFunction<IntExpr> b)
			{
				return c.mkNot(operand.express(c, b));
			}
			@Override public boolean interpret(Model m, IntFunction<IntExpr> b)
			{
				return !operand.interpret(m, b);
			}
			@Override public void register(boolean[] set)
			{
				operand.register(set);
			}
			@Override public String toString()
			{
				return "(not " + operand + ")";
			}
		};
	}

	@FunctionalInterface interface BinaryJunctor
	{
		boolean apply(boolean first, boolean second);
	}

	@FunctionalInterface interface TernaryFunction<A,B,C,R>
	{
		R apply(A a, B b, C c);
	}

	private static Proposition binary(
		TernaryFunction<Context,BoolExpr,BoolExpr,BoolExpr> x,
		BinaryJunctor o,
		String string,
		Proposition first,
		Proposition second)
	{
		return new Proposition()
		{
			@Override public BoolExpr express(Context c, IntFunction<IntExpr> b)
			{
				return x.apply(c, first.express(c, b), second.express(c, b));
			}
			@Override public boolean interpret(Model m, IntFunction<IntExpr> b)
			{
				return o.apply(first.interpret(m, b), second.interpret(m, b));
			}
			@Override public void register(boolean[] set)
			{
				first.register(set);
				second.register(set);
			}
			@Override public String toString()
			{
				return "(" + string + " " + first + " " + second + ")";
			}
		};
	}

	private static Proposition variadic(
		java.util.function.BiFunction<Context,BoolExpr[],BoolExpr> x,
		java.util.function.BiPredicate<Stream<Proposition>,java.util.function.Predicate<Proposition>> o,
		String string,
		Proposition[] operand)
	{
		return new Proposition()
		{
			@Override public BoolExpr express(Context c, IntFunction<IntExpr> b)
			{
				return x.apply(c, java.util.Arrays.stream(operand).map(x->x.express(c, b)).toArray(BoolExpr[]::new));
			}
			@Override public boolean interpret(Model m, IntFunction<IntExpr> e)
			{
				return o.test(java.util.Arrays.stream(operand), x->x.interpret(m, e));
			}
			@Override public void register(boolean[] set)
			{
				for(Proposition o: operand)
					o.register(set);
			}
			@Override public String toString()
			{
				StringBuilder s = new StringBuilder().append('(').append(string);
				for(Proposition o: operand)
					s.append(' ').append(o);
				return s.append(')').toString();
			}
		};
	}
}
