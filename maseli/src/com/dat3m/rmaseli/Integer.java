package com.dat3m.rmaseli;
import com.microsoft.z3.*;
import static java.lang.Integer.parseInt;

/**
 * Expression with integer value.
 * Template for expressions specialized for program states.
 */
public interface Integer
{

	IntExpr express(Context context, FuncDecl[] register, Expr state);

	int interpret(Model model, FuncDecl[] register, Expr state);

	void register(boolean[] set);

	/**
	 * Some register's value.
	 */
	static Integer get(int index)
	{
		return new Integer()
		{
			@Override public IntExpr express(Context c, FuncDecl[] r, Expr s)
			{
				return (IntExpr)r[index].apply(s);
			}
			@Override public int interpret(Model m, FuncDecl[] r, Expr s)
			{
				return parseInt(m.evaluate(r[index].apply(s), false).toString());
			}
			@Override public void register(boolean[] set)
			{
				set[index] = true;
			}
			@Override public String toString()
			{
				return "(register " + index + ")";
			}
		};
	}

	static Integer of(int value)
	{
		return new Integer()
		{
			@Override public IntExpr express(Context c, FuncDecl[] t, Expr s)
			{
				return c.mkInt(value);
			}
			@Override public int interpret(Model m, FuncDecl[] t, Expr s)
			{
				return value;
			}
			@Override public void register(boolean[] set)
			{
			}
			@Override public String toString()
			{
				return String.valueOf(value);
			}
		};
	}

	static Integer havoc(Object id)
	{
		return new Integer()
		{
			@Override public IntExpr express(Context c, FuncDecl[] t, Expr s)
			{
				return (IntExpr)c.mkFuncDecl("havoc-int-" + id, c.mkEventSort(), c.mkBoolSort()).apply(s);
			}
			@Override public int interpret(Model m, FuncDecl[] t, Expr s)
			{
				//TODO
				return 0;
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

	static Integer sum(Integer... operand)
	{
		return variadic((c,o)->(IntExpr)c.mkAdd(o), java.util.stream.IntStream::sum, "+", operand);
	}

	static Integer difference(Integer minuend, Integer subtrahend)
	{
		return binary((c,a,b)->(IntExpr)c.mkSub(a,b), (a,b)->a-b, "-", minuend, subtrahend);
	}

	static Integer product(Integer... operand)
	{
		return variadic((c,o)->(IntExpr)c.mkMul(o), o->o.reduce(1, (a,b)->a*b), "*", operand);
	}

	static Integer quotient(Integer dividend, Integer divisor)
	{
		return binary((c,a,b)->(IntExpr)c.mkDiv(a,b), (a,b)->a/b, "div", dividend, divisor);
	}

	static Integer remainder(Integer dividend, Integer divisor)
	{
		return binary(Context::mkRem, (a,b)->a%b, "rem", dividend, divisor);
	}

	static Integer condition(Proposition condition, Integer iftrue, Integer iffalse)
	{
		return new Integer()
		{
			@Override public IntExpr express(Context c, FuncDecl[] r, Expr s)
			{
				return (IntExpr)c.mkITE(condition.express(c, r, s), iftrue.express(c, r, s), iffalse.express(c, r, s));
			}
			@Override public int interpret(Model m, FuncDecl[] r, Expr s)
			{
				return condition.interpret(m, r, s) ? iftrue.interpret(m, r, s) : iffalse.interpret(m, r, s);
			}
			@Override public void register(boolean[] set)
			{
				condition.register(set);
				iftrue.register(set);
				iffalse.register(set);
			}
			@Override public String toString()
			{
				return "(ite " + condition + " " + iftrue + " " + iffalse + ")";
			}
		};
	}

	private static Integer binary(
		Proposition.TernaryFunction<Context,IntExpr,IntExpr,IntExpr> x,
		java.util.function.IntBinaryOperator o,
		String string,
		Integer first,
		Integer second)
	{
		return new Integer()
		{
			@Override public IntExpr express(Context c, FuncDecl[] r, Expr s)
			{
				return x.apply(c, first.express(c, r, s), second.express(c, r, s));
			}
			@Override public int interpret(Model m, FuncDecl[] r, Expr s)
			{
				return o.applyAsInt(first.interpret(m, r, s), second.interpret(m, r, s));
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

	private static Integer variadic(
		java.util.function.BiFunction<Context,IntExpr[],IntExpr> x,
		java.util.function.ToIntFunction<java.util.stream.IntStream> o,
		String string,
		Integer... operand)
	{
		return new Integer()
		{
			@Override public IntExpr express(Context c, FuncDecl[] r, Expr s)
			{
				return x.apply(c, java.util.Arrays.stream(operand).map(x->x.express(c, r, s)).toArray(IntExpr[]::new));
			}
			@Override public int interpret(Model m, FuncDecl[] r, Expr s)
			{
				return o.applyAsInt(java.util.Arrays.stream(operand).mapToInt(x->x.interpret(m, r, s)));
			}
			@Override public void register(boolean[] set)
			{
				for(Integer o: operand)
					o.register(set);
			}
			@Override public String toString()
			{
				StringBuilder s = new StringBuilder().append('(').append(string);
				for(Integer o: operand)
					s.append(' ').append(o);
				return s.append(')').toString();
			}
		};
	}
}
