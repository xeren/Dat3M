package com.dat3m.rmaseli;
import com.microsoft.z3.*;
import java.util.stream.Stream;

/**
 * Also known as formula or expression of a boolean value.
 * Template for expressions specialized for program states.
 */
public interface Proposition
{

	/**
	 * Constructs a representation in the solver's format.
	 * @param context
	 * Factory for expressions.
	 * @param register
	 * Access to the declared registers of the issuing thread.
	 * @param state
	 * Placeholder for states of the thread where this proposition is tested.
	 * Usually created by {@link com.microsoft.z3.Context#mkBound}.
	 * @return
	 * SMT-Expression of boolean sort.
	 */
	BoolExpr express(Context context, FuncDecl[] register, Expr state);

	/**
	 * Assigns values to all variables using a model.
	 * @param model
	 * Assignment of values to free variables.
	 * @param register
	 * Access to the declared registers of the issuing thread.
	 * @param state
	 * State to be queried.
	 * @return
	 * This proposition is satisfied by {@code m}.
	 */
	boolean interpret(Model model, FuncDecl[] register, Expr state);

	/**
	 * Those registers used in the proposition.
	 * @param set
	 * Bitset for registers.  Entry is set for each used register.
	 */
	void register(boolean[] set);

	/**
	 * Forms a non-deterministic formula allowing both results to happen in executions.
	 * @param id
	 * Identify nondeterministic results in a program.
	 * Does not identify a result in an execution unless with the event requesting it.
	 */
	static Proposition havoc(Object id)
	{
		return new Proposition()
		{
			@Override public BoolExpr express(Context c, FuncDecl[] r, Expr s)
			{
				return (BoolExpr)c.mkFuncDecl("havoc-bool-" + id, c.mkEventSort(), c.mkBoolSort()).apply(s);
			}
			@Override public boolean interpret(Model m, FuncDecl[] r, Expr s)
			{
				//TODO
				return false;
			}
			@Override public void register(boolean[] set)
			{
			}
			@Override public String toString()
			{
				return "(havoc)";
			}
		};
	}

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
			@Override public BoolExpr express(Context c, FuncDecl[] r, Expr s)
			{
				return c.mkNot(operand.express(c, r, s));
			}
			@Override public boolean interpret(Model m, FuncDecl[] r, Expr s)
			{
				return !operand.interpret(m, r, s);
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
		TernaryFunction<Context,BoolExpr,BoolExpr,BoolExpr> j,
		BinaryJunctor o,
		String string,
		Proposition first,
		Proposition second)
	{
		return new Proposition()
		{
			@Override public BoolExpr express(Context c, FuncDecl[] r, Expr s)
			{
				return j.apply(c, first.express(c, r, s), second.express(c, r, s));
			}
			@Override public boolean interpret(Model m, FuncDecl[] r, Expr s)
			{
				return o.apply(first.interpret(m, r, s), second.interpret(m, r, s));
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
		java.util.function.BiFunction<Context,BoolExpr[],BoolExpr> j,
		java.util.function.BiPredicate<Stream<Proposition>,java.util.function.Predicate<Proposition>> o,
		String string,
		Proposition[] operand)
	{
		return new Proposition()
		{
			@Override public BoolExpr express(Context c, FuncDecl[] r, Expr s)
			{
				return j.apply(c, java.util.Arrays.stream(operand).map(a->a.express(c, r, s)).toArray(BoolExpr[]::new));
			}
			@Override public boolean interpret(Model m, FuncDecl[] r, Expr s)
			{
				return o.test(java.util.Arrays.stream(operand), a->a.interpret(m, r, s));
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
