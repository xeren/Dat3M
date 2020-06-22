package com.dat3m.rmaseli;
import com.microsoft.z3.*;
import java.util.*;

/**
 * Decorates {@link com.microsoft.z3.Context} with
 */
public class Context extends com.microsoft.z3.Context
{
	private final Fixedpoint engine = mkFixedpoint();
	private final Sort sortEvent = mkIntSort();
	private final Sort[] sortEvent2 = new Sort[]{sortEvent, sortEvent};
	private final Map<Object,FuncDecl> namespace = new HashMap<>();

	public Sort mkEventSort()
	{
		return sortEvent;
	}

	public FuncDecl eventSet(String name)
	{
		return mkFuncDecl(name, sortEvent, mkBoolSort());
	}

	public FuncDecl eventRelation(String name)
	{
		return mkFuncDecl(name, sortEvent2, mkBoolSort());
	}

	public Expr next(Expr event)
	{
		assert mkIntSort().equals(sortEvent);
		return mkAdd((IntExpr)event, mkIntConst("threadcount"));
	}

	public FuncDecl predicate(Object id, Sort... domain)
	{
		FuncDecl f = namespace.get(id);
		if(null==f)
			namespace.put(id, f = mkFuncDecl("free-" + namespace.size(), domain, mkBoolSort()));
		else assert Arrays.equals(domain, f.getDomain());
		return f;
	}

	public void threads(int count)
	{
		engine.add(mkEq(mkIntConst("threadcount"), mkInt(count)));
	}

	public String toString()
	{
		StringBuilder r = new StringBuilder("(context");
		for(BoolExpr x: engine.getAssertions())
			r.append(' ').append(x);
		for(BoolExpr x: engine.getRules())
			r.append(' ').append(x);
		return r.append(')').toString();
	}

	public class Rule
	{

		private final Collection<Expr> bound = new LinkedList<>();

		public Expr newEvent()
		{
			Expr r = mkConst("bound"+bound.size(), sortEvent);
			bound.add(r);
			return r;
		}

		public void imply(BoolExpr premise0, BoolExpr premise1, BoolExpr conclusion)
		{
			imply(premise0, mkImplies(premise1, conclusion));
		}

		public void imply(BoolExpr premise, BoolExpr conclusion)
		{
			assume(mkImplies(premise, conclusion));
		}

		public void equalize(Expr first, Expr second)
		{
			assume(mkEq(first, second));
		}

		public void assume(BoolExpr proposition)
		{
			engine.addRule(mkForall(bound.toArray(Expr[]::new), proposition, 0, null, null, null, null), null);
		}
	}
}
