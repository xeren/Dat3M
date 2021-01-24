package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.utils.ThreadCache;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;

public class Thread implements Iterable<Event> {

	private final String name;
	private final int id;
	private final Event[] original;
	Event[] unrolled;

	private ThreadCache cache;

	public Thread(String name, int id, Event[] entry) {
		if(id < 0) {
			throw new IllegalArgumentException("Invalid thread ID");
		}
		if(entry == null) {
			throw new IllegalArgumentException("Thread entry event must be not null");
		}
		this.name = name;
		this.id = id;
		this.original = entry;
	}

	public String getName() {
		return name;
	}

	public int getId() {
		return id;
	}

	public ThreadCache getCache() {
		if(cache == null) {
			List<Event> events = Arrays.asList(unrolled);
			cache = new ThreadCache(events);
		}
		return cache;
	}

	@Override
	public Iterator<Event> iterator() {
		return Arrays.asList(unrolled).iterator();
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;

		if(obj == null || getClass() != obj.getClass())
			return false;

		return id == ((Thread) obj).id;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	/**
	Under-approximates the graph to a certain depth.
	The result is stored in this instance.
	@param bound
	Amount of backwards-jumps allowed for executions of this program.
	*/
	void unroll(int bound) {
		Label[] e = new Label[original.length];
		ArrayList<Event> r = new ArrayList<>(bound * e.length);
		boolean reachable = true;
		while(bound > 0) {
			Load latest = null;
			for(int i = 0; i < e.length; i++) {
				if(null != e[i]) {
					r.add(e[i]);
					e[i] = null;
					reachable = true;
				}
				else if(reachable) {
					if(original[i] instanceof CondJump) {
						CondJump c = (CondJump) original[i];
						Label l = c.getLabel();
						int j = 0;
						while(l != original[j]) {
							++j;
							assert j < original.length;
						}
						assert l == original[j];
						if(null == e[j])
							e[j] = l.getCopy();
						r.add(new CondJump(c, e[j]));
						reachable = !c.isUnconditional();
					}
					else if(original[i] instanceof RMWStore) {
						RMWStore s = (RMWStore) original[i];
						//latest load event is a copy of s.loadEvent
						assert null != latest;
						r.add(new RMWStore(s, latest));
					}
					else {
						r.add(original[i].getCopy());
						if(original[i] instanceof Load)
							latest = (Load) r.get(r.size() - 1);
					}
				}
			}
			reachable = false;
			bound--;
		}
		for(int i = 0; i < e.length; i++) {
			if(null != e[i]) {
				r.add(e[i]);
				reachable = true;
			}
			else if(reachable) {
				reachable = !(original[i] instanceof CondJump);
				r.add(reachable ? original[i].getCopy() : new BoundEvent());
			}
		}
		unrolled = r.toArray(new Event[0]);
		cache = null;
	}


	// Encoding
	// -----------------------------------------------------------------------------------------------------------------

	/**
	Expresses this thread's control flow as a boolean formula.
	@param context
	Builder for expressions.
	@return
	Conjunction of additional conditions.
	*/
	BoolExpr encodeCF(Context context) {
		ArrayList<BoolExpr> out = new ArrayList<>();
		HashMap<Integer,LinkedList<BoolExpr>> message = new HashMap<>();
		BoolExpr cf = context.mkTrue();
		for(Event e : unrolled) {
			LinkedList<BoolExpr> m = message.remove(e.getCId());
			if(null != m) {
				if(null != cf)
					m.add(cf);
				cf = context.mkBoolConst("cf " + e.getCId());
				out.add(context.mkEq(cf, context.mkOr(m.toArray(new BoolExpr[0]))));
			}
			e.encode(context, out::add, cf);
			if(e instanceof CondJump) {
				BoolExpr condition = context.mkBoolConst("branch " + e.getCId());
				out.add(context.mkEq(condition, ((CondJump) e).getCondition().toZ3Bool(e, context)));
				message.computeIfAbsent(((CondJump) e).getLabel().getCId(), k->new LinkedList<>()).add(context.mkAnd(cf, condition));
				message.computeIfAbsent(1 + e.getCId(), k->new LinkedList<>()).add(context.mkAnd(cf, context.mkNot(condition)));
				cf = null;
			}
		}
		return context.mkAnd(out.toArray(new BoolExpr[0]));
	}
}
