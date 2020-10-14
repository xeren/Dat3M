package com.dat3m.dartagnan.wmm.filter;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public abstract class FilterAbstract {

	protected String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void initialise() {
	}

	public abstract boolean filter(Event e);

	public void encodeFO(EncodeContext context, ProgramCache cache) {
		if(null == name)
			return;
		Expr a = context.bind(0);
		termFO(0).forEach(c->{
			//assert no edges nor free variables
			LinkedList<BoolExpr> enc = new LinkedList<>();
			c.set((n,m)->enc.add(context.unary(n).of(a)));
			context.ruleForall(List.of(a), enc, context.unary(name).of(a));
		});
	}

	public final Stream<Clause> nameFO(int variable) {
		return null == name ? termFO(variable) : Stream.of(Clause.set(name, variable));
	}

	protected abstract Stream<Clause> termFO(int variable);
}