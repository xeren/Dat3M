package com.dat3m.rmaseli;
import com.microsoft.z3.*;

/**
 * Binary relations over events in a computation.
 * Closed under boolean operations and monoid operations.
 * <p>
 * {@code int} relates events issued by the same thread.
 * {@code ext} is its complement, relating events issued by different threads.
 * {@code po} is a sub relation of {@code int}, where .
 * {@code loc} relates memory events accessing the same memory address.
 * {@code rf} relates write events to read events reading their value.
 * {@code co} describes overwrites.
 * {@code fr=(join (inv rf) co)} relates read events to late write events accessing the same memory address.
 * {@code coi=(and co int)}, {@code coe=(and co ext)}
 * {@code rfi=(and rf int)}, {@code rfe=(and rf ext)}
 * {@code po-loc=(and po loc)}
 */
public interface Communication
{

	/**
	 *
	 * @param context
	 * Manager of expressions.
	 * @param rule
	 * Quantified proposition currently worked on.
	 * @param first
	 * Placeholder for domain-side events.
	 * @param second
	 * Placeholder for range-side events.
	 * @return
	 * Proposition to be satisfied by
	 */
	BoolExpr express(Context context, Context.Rule rule, Expr first, Expr second);

	/**
	 * Refers to a relation by name.
	 * Also used to refer to static relationships.
	 * @param name
	 * Identifier bound to some expression or predefined.
	 * @see Communication
	 */
	static Communication of(String name)
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				return (BoolExpr)c.eventRelation(name).apply(a, b);
			}
			@Override public String toString()
			{
				return name;
			}
		};
	}

	/**
	 * Forms the intersection of two relations.
	 */
	static Communication and(Communication first, Communication second)
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				return c.mkAnd(first.express(c, r, a, b), second.express(c, r, a, b));
			}
			@Override public String toString()
			{
				return "(and " + first + " " + second + ")";
			}
		};
	}

	/**
	 * Forms the union of two relations.
	 */
	static Communication or(Communication first, Communication second)
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				return c.mkOr(first.express(c, r, a, b), second.express(c, r, a, b));
			}
			@Override public String toString()
			{
				return "(or " + first + " " + second + ")";
			}
		};
	}

	/**
	 * Forms the complement of a relation.
	 */
	static Communication not(Communication operand)
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				FuncDecl defined = c.eventSet("is-defined");
				return c.mkAnd((BoolExpr)defined.apply(a), (BoolExpr)defined.apply(b), c.mkNot(operand.express(c, r, a, b)));
			}
			@Override public String toString()
			{
				return "(not " + operand + ")";
			}
		};
	}

	/**
	 * Forms the empty relation.
	 */
	static Communication empty()
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				return c.mkFalse();
			}
			@Override public String toString()
			{
				return "empty";
			}
		};
	}

	/**
	 * Forms the composition of two relations.
	 */
	static Communication join(Communication first, Communication second)
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				Expr d = r.newEvent();
				return c.mkAnd(first.express(c, r, a, d), second.express(c, r, d, b));
			}
			@Override public String toString()
			{
				return "(join " + first + " " + second + ")";
			}
		};
	}

	/**
	 * Forms the inversion of a relation.
	 */
	static Communication inv(Communication operand)
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				return operand.express(c, r, b, a);
			}
			@Override public String toString()
			{
				return "(inv " + operand + ")";
			}
		};
	}

	/**
	 * Forms the identity relation.
	 */
	static Communication id()
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				return c.mkEq(a, b);
			}
			@Override public String toString()
			{
				return "id";
			}
		};
	}

	/**
	 * Forms the transitive closure of a relation.
	 */
	static Communication repeat(Communication operand)
	{
		return new Communication()
		{
			@Override
			public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				FuncDecl inner = c.eventRelation("inner"); //TODO choose name
				FuncDecl outer = c.eventRelation("outer");
				let(c, "inner", operand);
				expressFirst(c, inner, outer);
				expressNext(c, inner, outer);
				return (BoolExpr)outer.apply(a, b);
			}
			private void expressFirst(Context c, FuncDecl inner, FuncDecl outer)
			{
				Context.Rule r = c.new Rule();
				Expr x = r.newEvent();
				Expr y = r.newEvent();
				r.imply((BoolExpr)inner.apply(x, y), (BoolExpr)outer.apply(x, y));
			}
			private void expressNext(Context c, FuncDecl inner, FuncDecl outer)
			{
				Context.Rule r = c.new Rule();
				Expr x = r.newEvent();
				Expr y = r.newEvent();
				Expr z = r.newEvent();
				r.imply((BoolExpr)inner.apply(x, y), (BoolExpr)outer.apply(y, z), (BoolExpr)outer.apply(x, z));
			}
			@Override
			public String toString()
			{
				return "(transitive " + operand + ")";
			}
		};
	}

	/**
	 * Forms the cartesian product of two sets of events.
	 */
	static Communication full(Set domain, Set range)
	{
		return new Communication()
		{
			@Override public BoolExpr express(Context c, Context.Rule r, Expr a, Expr b)
			{
				return c.mkAnd(domain.express(c, a), range.express(c, b));
			}
			@Override public String toString()
			{
				return "(full " + domain + " " + range + ")";
			}
		};
	}

	/**
	 * Expression of event sets.
	 * Contains predefined event sets and is closed under boolean operations.
	 */
	interface Set
	{

		BoolExpr express(Context context, Expr element);

		/**
		 * @param name
		 * Either "read", "write", "local", "fence", "branch", "memory" or "defined".
		 */
		static Set of(String name)
		{
			assert java.util.List.of("read", "write", "local", "fence", "branch", "memory", "defined").contains(name);
			return new Set()
			{
				@Override public BoolExpr express(Context c, Expr e)
				{
					return (BoolExpr)c.eventSet("is-" + name).apply(e);
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
				@Override public BoolExpr express(Context c, Expr e)
				{
					return c.mkAnd(first.express(c, e), second.express(c, e));
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
				@Override public BoolExpr express(Context c, Expr e)
				{
					return c.mkOr(first.express(c, e), second.express(c, e));
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
				@Override public BoolExpr express(Context c, Expr e)
				{
					return c.mkNot(operand.express(c, e));
				}
				@Override public String toString()
				{
					return "(not " + operand + ")";
				}
			};
		}
	}

	/**
	 * Proposes that this expression yields an acyclic relation in a consistent computation.
	 * A relation is acyclic iff its transitive closure relates no entity with itself.
	 * @param relation
	 * Expression of relations over computation events.
	 */
	static void acyclic(Context context, Communication relation)
{
	irreflexive(context, repeat(relation));
}

	/**
	 * Proposes that this expression yields an irreflexive relation in a consistent computation.
	 * A relation is irreflexive iff it relates no entity with itself.
	 * @param relation
	 * Expression of relations over computation events.
	 */
	static void irreflexive(Context context, Communication relation)
	{
		// empty(and(relation, id()));
		Context.Rule rule = context.new Rule();
		Expr x = rule.newEvent();
		rule.assume(context.mkNot(relation.express(context, rule, x, x)));
	}

	/**
	 * Proposes that an expression yields the empty relation in a consistent computation.
	 * @param relation
	 * Expression of relations over computation events.
	 */
	static void empty(Context context, Communication relation)
	{
		Context.Rule rule = context.new Rule();
		Expr x = rule.newEvent();
		Expr y = rule.newEvent();
		rule.assume(context.mkNot(relation.express(context, rule, x, y)));
	}

	/**
	 * Binds a name to a relation.
	 * The relation becomes referencable by other definitions and axioms.
	 * @param name
	 * Identifier to refer to the defined expression.
	 * @param relation
	 * Expression of relations over computation events.
	 */
	static void let(Context context, String name, Communication relation)
	{
		Context.Rule rule = context.new Rule();
		Expr x = rule.newEvent();
		Expr y = rule.newEvent();
		//TODO try equivalence if there are no more bound variables
		rule.imply(
			relation.express(context, rule, x, y),
			(BoolExpr)context.eventRelation("rel-" + name).apply(x, y)
		);
	}

	/**
	 * {@code is-memory(A) :- is-read(A) .}
	 * {@code is-memory(A) :- is-write(A) .}
	 * {@code is-defined(A) :- is-memory(A) .}
	 * {@code is-defined(A) :- is-local(A) .}
	 * {@code is-defined(A) :- is-fence(A) .}
	 * {@code is-defined(A) :- is-branch(A) .}
	 * {@code is-undefined(A) :- ¬is-defined(A) .}
	 */
	static void defineEventType(Context context)
	{
		Context.Rule rule = context.new Rule();
		Expr x = rule.newEvent();
		Expr read = context.eventSet("is-read").apply(x);
		Expr write = context.eventSet("is-write").apply(x);
		Expr local = context.eventSet("is-local").apply(x);
		Expr fence = context.eventSet("is-fence").apply(x);
		Expr branch = context.eventSet("is-branch").apply(x);
		Expr memory = context.eventSet("is-memory").apply(x);
		Expr defined = context.eventSet("is-defined").apply(x);
		Expr undefined = context.eventSet("is-undefined").apply(x);
		rule.assume(context.mkAnd(
			context.mkDistinct(read, write, local, fence, branch, undefined),
			context.mkEq(memory, context.mkOr((BoolExpr)read, (BoolExpr)write)),
			context.mkEq(defined, context.mkOr((BoolExpr)memory, (BoolExpr)local, (BoolExpr)fence, (BoolExpr)branch))
		));
	}

	/**
	 * {@code loc(A,B) :- mem(A,K,V) , mem(B,K,W) .}
	 */
	static void defineLocation(Context context)
	{
		Context.Rule rule = context.new Rule();
		Expr x = rule.newEvent();
		Expr y = rule.newEvent();
		FuncDecl key = context.mkFuncDecl("key", context.mkEventSort(), context.mkIntSort());
		rule.equalize(
			context.eventRelation("rel-loc").apply(x, y),
			context.mkAnd(
				(BoolExpr)context.eventSet("is-memory").apply(x),
				(BoolExpr)context.eventSet("is-memory").apply(y),
				context.mkEq(key.apply(x), key.apply(y))
			)
		);
	}

	/**
	 * Defines the read-from relation between write events and read events.
	 * {@code rf(W,R) -:- w(W) , r(R) , mem(W,K,V) , mem(R,K,V) .}
	 * satisfied(R) :- rf(W,R)
	 * mem(R,K,0) :- r(R) , -satisfied(R)
	 * =(V,W) , =(K,L) :- r(R) , mem(R,K,V) , mem(R,L,W)
	 */
	static void defineCommunication(Context context)
	{
		// origin:: Read -> Store
		Context.Rule rule = context.new Rule();
		Expr r = rule.newEvent();
		Expr origin = context.mkFuncDecl("origin", context.mkEventSort(), context.mkEventSort()).apply(r);
		FuncDecl key = context.mkFuncDecl("key", context.mkEventSort(), context.mkIntSort());
		FuncDecl value = context.mkFuncDecl("value", context.mkEventSort(), context.mkIntSort());
		rule.imply(
			(BoolExpr)context.eventSet("is-read").apply(r),
			context.mkAnd(
				(BoolExpr)context.eventSet("is-write").apply(origin),
				context.mkEq(key.apply(r), key.apply(origin)),
				context.mkEq(value.apply(r), value.apply(origin))
			)
		);
	}

	/**
	 * For each memory address, some order over write events on that address.
	 */
	static void defineReadFrom(Context context)
	{
		// (rf w r) iff (= w (origin r))
		Context.Rule rule = context.new Rule();
		Expr w = rule.newEvent();
		Expr r = rule.newEvent();
		rule.equalize(
			context.eventRelation("rel-rf").apply(w, r),
			context.mkAnd(
				(BoolExpr)context.eventSet("is-read").apply(r),
				context.mkEq(w, context.mkFuncDecl("origin", context.mkEventSort(), context.mkEventSort()).apply(r))
			)
		);
	}

	/**
	 * Defines the internal relation 'int'.
	 * This requires an integer constant named 'threadcount'.
	 */
	static void defineInternal(Context context)
	{
		assert context.mkEventSort().equals(context.mkIntSort());
		Context.Rule rule = context.new Rule();
		Expr x = rule.newEvent();
		Expr y = rule.newEvent();
		rule.equalize(
			context.eventRelation("rel-int").apply(x, y),
			context.mkAnd(
				(BoolExpr)context.eventSet("is-defined").apply(x),
				(BoolExpr)context.eventSet("is-defined").apply(y),
				context.mkEq(
					context.mkMod((IntExpr)x, context.mkIntConst("threadcount")),
					context.mkMod((IntExpr)y, context.mkIntConst("threadcount"))
				)
			)
		);
	}

	/**
	 * Defines the external relation 'ext'.
	 * This requires an integer constant named 'threadcount'.
	 */
	static void defineExternal(Context context)
	{
		assert context.mkEventSort().equals(context.mkIntSort());
		Context.Rule rule = context.new Rule();
		Expr x = rule.newEvent();
		Expr y = rule.newEvent();
		rule.equalize(
			context.eventRelation("rel-ext").apply(x, y),
			context.mkAnd(
				(BoolExpr)context.eventSet("is-defined").apply(x),
				(BoolExpr)context.eventSet("is-defined").apply(y),
				context.mkDistinct(
					context.mkMod((IntExpr)x, context.mkIntConst("threadcount")),
					context.mkMod((IntExpr)y, context.mkIntConst("threadcount"))
				)
			)
		);
	}
}
