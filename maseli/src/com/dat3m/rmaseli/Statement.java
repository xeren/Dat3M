package com.dat3m.rmaseli;
import com.microsoft.z3.*;

/**
 * Control statements defined by arbitrary programs.
 */
public interface Statement
{

	/**
	 * Instantiates new events based on this statement.
	 * Called during unrolling of a program.
	 */
	void express(Context context, FuncDecl[] register);

	static Statement hold()
	{
		return new Statement()
		{
			@Override public void express(Context x, FuncDecl[] r)
			{
			}
			@Override public String toString()
			{
				return "(hold)";
			}
		};
	}

	/**
	 * @param key
	 * Thread-local index of the register providing the address.
	 * @param value
	 * Thread-local index of the register receiving the value.
	 * @return
	 * Factory producing one read event per iteration.
	 */
	static Statement readRelaxed(int registercount, Integer key, int value, Statement then)
	{
		boolean[] dependency = new boolean[registercount];
		key.register(dependency);
		return new Statement()
		{
			@Override public void express(Context c, FuncDecl[] r)
			{
				FuncDecl p = c.predicate(this);
				FuncDecl q = c.predicate(then);
				Context.Rule rule = c.new Rule();
				Expr state = rule.newEvent();
				Expr next = rule.newEvent();
				rule.imply(
					c.mkEq(next, c.next(state)),
					(BoolExpr)p.apply(state),
					c.mkAnd(
						(BoolExpr)c.eventSet("is-write").apply(state),
						(BoolExpr)q.apply(next),
						c.mkEq(c.mkFuncDecl("key", c.mkEventSort(), c.mkIntSort()).apply(state), key.express(c, r, state)),
						c.mkEq(c.mkFuncDecl("value", c.mkEventSort(), c.mkIntSort()).apply(state), r[value].apply(next)),
						c.mkAnd(streamExcept(r, value).map(R->c.mkEq(R.apply(state), R.apply(next))).toArray(BoolExpr[]::new))
					)
				);
			}
			@Override public String toString()
			{
				return "(read relaxed " + key + " " + value + ")";
			}
		};
	}

	/**
	 * @param key
	 * Thread-local index of the register providing the address.
	 * @param value
	 * Thread-local index of the register providing the written value.
	 * @return
	 * Factory producing one write event per iteration.
	 */
	static Statement writeRelaxed(int registercount, Integer key, Integer value, Statement next)
	{
		boolean[] dependencyKey = new boolean[registercount];
		key.register(dependencyKey);
		boolean[] dependencyValue = new boolean[registercount];
		value.register(dependencyValue);
		return new Statement()
		{
			@Override public void express(Context c, FuncDecl[] r)
			{
				Context.Rule rule = c.new Rule();
				Expr state = rule.newEvent();
				Expr next = rule.newEvent();
				rule.imply(
					c.mkEq(next, c.next(state)),
					(BoolExpr)c.predicate(this, c.mkEventSort()).apply(state),
					c.mkAnd(
						(BoolExpr)c.eventSet("is-write").apply(state),
						(BoolExpr)c.predicate(next, c.mkEventSort()).apply(next),
						c.mkAnd(java.util.Arrays.stream(r).map(R->c.mkEq(R.apply(state), R.apply(next))).toArray(BoolExpr[]::new))
					)
				);
			}
			@Override public String toString()
			{
				return "(write relaxed " + key + " " + value + ")";
			}
		};
	}

	static Statement local(int registercount, int destination, Integer expression, Statement next)
	{
		boolean[] dependency = new boolean[registercount];
		expression.register(dependency);
		return new Statement()
		{
			@Override public void express(Context c, FuncDecl[] r)
			{
				Context.Rule rule = c.new Rule();
				Expr state = rule.newEvent();
				Expr next = rule.newEvent();
				rule.imply(
					c.mkEq(next, c.next(state)),
					(BoolExpr)c.predicate(this, c.mkEventSort()).apply(state),
					c.mkAnd(
						(BoolExpr)c.eventSet("is-local").apply(state),
						(BoolExpr)c.predicate(next, c.mkEventSort()).apply(next),
						c.mkEq(r[destination].apply(next), expression.express(c, r, state)),
						c.mkAnd(streamExcept(r, destination).map(R->c.mkEq(R.apply(state), R.apply(next))).toArray(BoolExpr[]::new))
					)
				);
			}
			@Override public String toString()
			{
				return "(local " + destination + " " + expression + " " + java.util.Arrays.toString(dependency) + ")";
			}
		};
	}

	static Statement branch(int registercount, Proposition condition, Statement then, Statement otherwise)
	{
		boolean[] dependency = new boolean[registercount];
		condition.register(dependency);
		return new Statement()
		{
			@Override public void express(Context c, FuncDecl[] r)
			{
				Context.Rule rule = c.new Rule();
				Expr state = rule.newEvent();
				Expr next = rule.newEvent();
				rule.imply(
					c.mkEq(next, c.next(state)),
					(BoolExpr)c.predicate(this, c.mkEventSort()).apply(state),
					c.mkAnd(
						(BoolExpr)c.eventSet("is-branch").apply(state),
						(BoolExpr)c.mkITE(
							condition.express(c, r, state),
							c.predicate(then, c.mkEventSort()).apply(next),
							c.predicate(otherwise, c.mkEventSort()).apply(next)
						),
						c.mkAnd(java.util.Arrays.stream(r).map(R->c.mkEq(R.apply(state), R.apply(next))).toArray(BoolExpr[]::new))
					)
				);
			}
		};
	}

	static Statement choice(Statement then, Statement otherwise)
	{
		return new Statement()
		{
			@Override public void express(Context c, FuncDecl[] r)
			{
				Context.Rule rule = c.new Rule();
				Expr state = rule.newEvent();
				Expr next = rule.newEvent();
				rule.imply(
					c.mkEq(next, c.next(state)),
					(BoolExpr)c.predicate(this, c.mkEventSort()).apply(state),
					c.mkAnd(
						(BoolExpr)c.eventSet("is-branch").apply(state),
						c.mkOr(
							(BoolExpr)c.predicate(then, c.mkEventSort()).apply(next),
							(BoolExpr)c.predicate(otherwise, c.mkEventSort()).apply(next)
						),
						c.mkAnd(java.util.Arrays.stream(r).map(R->c.mkEq(R.apply(state), R.apply(next))).toArray(BoolExpr[]::new))
					)
				);
			}
		};
	}

	private static <T> java.util.stream.Stream<T> streamExcept(T[] a, int i)
	{
		return java.util.stream.Stream.concat(
			java.util.Arrays.stream(a, 0, i),
			java.util.Arrays.stream(a, i + 1, a.length)
		);
	}
}
