package com.dat3m.rmaseli;
import com.microsoft.z3.*;

/**
 * Binary relations over events in a computation.
 */
public interface Communication
{

	BoolExpr definition(Context c, Expr first, Expr second);

	default void definition(com.microsoft.z3.Context context, Fixedpoint engine, String name)
	{
		Context c = new Context(context);
		Expr x = c.bind(c.sortEvent);
		Expr y = c.bind(c.sortEvent);
		BoolExpr r = context.mkImplies(definition(c, x, y), c.link("rel-" + name, c.sortRelation, x, y));
		engine.addRule(c.forall(r), context.mkSymbol("let-" + name));
	}

	static void defineEventType(com.microsoft.z3.Context context, Fixedpoint engine)
	{
		Context c = new Context(context);
		Expr x = c.bind(c.sortEvent);
		BoolExpr rule = context.mkDistinct(
			c.link("is-read", c.sortEvent, x),
			c.link("is-write", c.sortEvent, x),
			c.link("is-local", c.sortEvent, x),
			c.link("is-fence", c.sortEvent, x),
			c.link("is-branch", c.sortEvent, x),
			c.link("is-undefined", c.sortEvent, x));
		engine.addRule(c.forall(rule), context.mkSymbol("event-type"));

		rule = context.mkEq(
			c.link("is-memory", c.sortEvent, x),
			context.mkOr(
				c.link("is-read", c.sortEvent, x),
				c.link("is-write", c.sortEvent, x)));
		engine.addRule(c.forall(rule), context.mkSymbol("event-memory"));

		rule = context.mkEq(
			c.link("is-defined", c.sortEvent, x),
			context.mkOr(
				c.link("is-memory", c.sortEvent, x),
				c.link("is-local", c.sortEvent, x),
				c.link("is-fence", c.sortEvent, x),
				c.link("is-branch", c.sortEvent, x)));
		engine.addRule(c.forall(rule), context.mkSymbol("event-defined"));
	}

	/**
	 * {@code loc(A,B) :- mem(A,K,V) , mem(B,K,W) .}
	 */
	static void defineLoc(com.microsoft.z3.Context context, Fixedpoint engine)
	{
		Context c = new Context(context);
		Expr a = c.bind(c.sortEvent);
		Expr b = c.bind(c.sortEvent);
		BoolExpr rule = context.mkEq(
			c.link("rel-loc", c.sortRelation, a, b),
			context.mkAnd(
				c.link("is-memory", c.sortEvent, a),
				c.link("is-memory", c.sortEvent, b),
				context.mkEq(c.key(a), c.key(b))));
		engine.addRule(c.forall(rule), context.mkSymbol("loc"));
	}

	/**
	 * Defines the read-from relation between write events and read events.
	 * {@code rf(W,R) -:- w(W) , r(R) , mem(W,K,V) , mem(R,K,V) .}
	 * satisfied(R) :- rf(W,R)
	 * mem(R,K,0) :- r(R) , -satisfied(R)
	 * =(V,W) , =(K,L) :- r(R) , mem(R,K,V) , mem(R,L,W)
	 */
	static void defineCommunication(com.microsoft.z3.Context context, Fixedpoint engine)
	{
		Context c = new Context(context);
		Expr r = c.bind(c.sortEvent);
		Expr origin = context.mkApp(context.mkFuncDecl("origin", c.sortEvent, c.sortEvent), r);
		BoolExpr rule = c.implies(
			c.link("is-read", c.sortEvent, r),
			context.mkAnd(
				c.link("is-write", c.sortEvent, origin),
				context.mkEq(c.key(r), c.key(origin)),
				context.mkEq(c.value(r), c.value(origin))));
		engine.addRule(c.forall(rule), context.mkSymbol("rf-satisfaction"));

		Expr w = c.bind(c.sortEvent);
		rule = context.mkEq(
			c.link("rf", c.sortRelation, w, r),
			context.mkAnd(
				c.link("is-read", c.sortEvent, r),
				context.mkEq(w, origin)));
		engine.addRule(c.forall(rule), context.mkSymbol("rf-function"));
	}

	/**
	 * Defines the internal and external relations 'int' and 'ext'.
	 */
	static void defineInternal(com.microsoft.z3.Context context, Fixedpoint engine)
	{
		Context c = new Context(context);
		Expr x = c.bind(c.sortEvent);
		Expr y = c.bind(c.sortEvent);
		Expr z = c.bind(c.sortEvent); //TODO existential
		BoolExpr rule = context.mkEq(
			c.link("rel-int", c.sortRelation, x, y),
			context.mkOr(
				c.link("rel-po", c.sortRelation, x, y),
				c.link("rel-int", c.sortRelation, y, x),
				context.mkAnd(
					c.link("rel-int", c.sortRelation, x, z),
					c.link("rel-int", c.sortRelation, z, y)
				)
			)
		);
		engine.addRule(c.forall(rule), context.mkSymbol("int"));
	}

	/**
	 * Refers to a relation by name.
	 * Also used to refer to static relationships.
	 */
	static Communication of(String name)
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				return c.link(name, c.sortMemory, a, b);
			}
			@Override public String toString()
			{
				return name;
			}
		};
	}

	static Communication and(Communication first, Communication second)
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				return c.c.mkAnd(first.definition(c, a, b), second.definition(c, a, b));
			}
			@Override public String toString()
			{
				return "(and " + first + " " + second + ")";
			}
		};
	}

	static Communication or(Communication first, Communication second)
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				return c.c.mkOr(first.definition(c, a, b), second.definition(c, a, b));
			}
			@Override public String toString()
			{
				return "(or " + first + " " + second + ")";
			}
		};
	}

	static Communication not(Communication operand)
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				return c.c.mkNot(operand.definition(c, a, b));
			}
			@Override public String toString()
			{
				return "(not " + operand + ")";
			}
		};
	}

	static Communication empty()
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				return c.c.mkFalse();
			}
			@Override public String toString()
			{
				return "empty";
			}
		};
	}

	static Communication join(Communication first, Communication second)
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				Expr d = c.bind(c.sortEvent);
				return c.c.mkAnd(first.definition(c, a, d), second.definition(c, d, b));
			}
			@Override public String toString()
			{
				return "(join " + first + " " + second + ")";
			}
		};
	}

	static Communication inv(
		Communication operand)
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				return operand.definition(c, b, a);
			}
			@Override public String toString()
			{
				return "(inv " + operand + ")";
			}
		};
	}

	static Communication id()
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				return c.c.mkEq(a, b);
			}
			@Override public String toString()
			{
				return "id";
			}
		};
	}

	static Communication full(Set domain, Set range)
	{
		return new Communication()
		{
			@Override public BoolExpr definition(Context c, Expr a, Expr b)
			{
				return c.c.mkAnd(domain.definition(c, a), range.definition(c, b));
			}
			@Override public String toString()
			{
				return "(full " + domain + " " + range + ")";
			}
		};
	}

	/**
	 * Event set.
	 */
	interface Set
	{

		BoolExpr definition(Context context, Expr element);

		static Set any()
		{
			return new Set()
			{
				@Override public BoolExpr definition(Context c, Expr e)
				{
					return c.c.mkTrue();
				}
				@Override public String toString()
				{
					return "any";
				}
			};
		}

		/**
		 * @param name
		 * Either "read", "write", "local" or "branch".
		 */
		static Set name(String name)
		{
			return new Set()
			{
				@Override public BoolExpr definition(Context c, Expr e)
				{
					return c.link(name, c.sortNoMemory, e);
				}
				@Override public String toString()
				{
					return name;
				}
			};
		}

		static Set and(Set first, Set second)
		{
			return new Set()
			{
				@Override public BoolExpr definition(Context c, Expr e)
				{
					return c.c.mkAnd(first.definition(c, e), second.definition(c, e));
				}
				@Override public String toString()
				{
					return "(and " + first + " " + second + ")";
				}
			};
		}

		static Set or(Set first, Set second)
		{
			return new Set()
			{
				@Override public BoolExpr definition(Context c, Expr e)
				{
					return c.c.mkOr(first.definition(c, e), second.definition(c, e));
				}
				@Override public String toString()
				{
					return "(or " + first + " " + second + ")";
				}
			};
		}

		static Set not(Set operand)
		{
			return new Set()
			{
				@Override public BoolExpr definition(Context c, Expr e)
				{
					return c.c.mkNot(operand.definition(c, e));
				}
				@Override public String toString()
				{
					return "(not " + operand + ")";
				}
			};
		}
	}

	class Context
	{
		final com.microsoft.z3.Context c;
		final Sort sortBool;
		final Sort sortInt;
		final Sort sortEvent;
		final Sort[] sortNoMemory;
		final Sort[] sortMemory;
		final Sort[] sortRelation;
		final java.util.ArrayList<Expr> bound = new java.util.ArrayList<>();
		final FuncDecl functionKey;
		final FuncDecl functionValue;

		Context(com.microsoft.z3.Context c)
		{
			this.c = c;
			sortBool = c.mkBoolSort();
			sortInt = c.mkIntSort();
			sortEvent = c.mkIntSort();
			sortNoMemory = new Sort[]{sortEvent};
			sortMemory = new Sort[]{sortEvent, sortInt, sortInt};
			sortRelation = new Sort[]{sortEvent, sortEvent};
			functionKey = c.mkFuncDecl("key", sortEvent, sortInt);
			functionValue = c.mkFuncDecl("value", sortEvent, sortInt);
		}

		Expr bind(Sort sort)
		{
			Expr r = c.mkBound(bound.size(), sort);
			bound.add(r);
			return r;
		}

		BoolExpr link(String name, Sort[] sort, Expr... value)
		{
			return (BoolExpr)c.mkApp(c.mkFuncDecl(name, sort, sortBool), value);
		}

		BoolExpr link(String name, Sort sort, Expr value)
		{
			return (BoolExpr)c.mkApp(c.mkFuncDecl(name, sort, sortBool), value);
		}

		Expr key(Expr event)
		{
			return c.mkApp(functionKey, event);
		}

		Expr value(Expr event)
		{
			return c.mkApp(functionValue, event);
		}

		BoolExpr implies(BoolExpr premise, BoolExpr conclusion)
		{
			return c.mkImplies(premise, conclusion);
		}

		BoolExpr implies(BoolExpr premise0, BoolExpr premise1, BoolExpr conclusion)
		{
			return c.mkImplies(c.mkAnd(premise0, premise1), conclusion);
		}

		BoolExpr forall(BoolExpr proposition)
		{
			return c.mkForall(bound.toArray(Expr[]::new), proposition, 0, null, null, null, null);
		}

	}
}
