package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.program.event.BoundEvent;
import com.dat3m.dartagnan.program.event.CondJump;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Label;
import com.dat3m.dartagnan.program.utils.ThreadCache;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;
import java.util.stream.IntStream;

public class Thread implements Iterable<Event> {

	private final String name;
	private final int id;
	private final Event[] original;
	Event[] unrolled;
	Event[] compiled;

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
			List<Event> events = Arrays.asList(compiled);
			cache = new ThreadCache(events);
		}
		return cache;
	}

	@Override
	public Iterator<Event> iterator() {
		return Arrays.asList(compiled).iterator();
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
		if(Arrays.stream(original).noneMatch(e->e instanceof CondJump && e.getOId() < ((CondJump)e).getLabel().getOId())) {
			unrolled = original;
			cache = null;
			return;
		}
		int start = original[0].getCId();
		assert IntStream.range(0, original.length).allMatch(i->start+i==original[i].getCId());
		Label[] e = new Label[original.length];
		ArrayList<Event> r = new ArrayList<>(bound * e.length);
		boolean reachable = true;
		while(bound > 0) {
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
						int j = l.getCId() - start;
						assert l == original[j];
						if(null == e[j])
							e[j] = l.getCopy();
						r.add(new CondJump(c, e[j]));
						reachable = !c.isUnconditional();
					}
					else {
						r.add(original[i].getCopy());
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


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	/**
	Tries to substitutes all abstract events that can be expressed alternatively in a give architecture.
	The result is stored in this instance.
	@param target
	Describes an instruction set.
	*/
	void compile(Arch target) {
		ArrayList<Event> r = new ArrayList<>();
		for(Event e : unrolled) {
			Event[] substitute = e.compile(target);
			if(null == substitute)
				r.add(e);
			else for(Event c : substitute) {
				c.setOId(e.getOId());
				c.setCLine(e.getCLine());
				c.setUId(e.getUId());
				r.add(c);
			}
		}
		compiled = r.toArray(new Event[0]);
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
		for(Event e : compiled) {
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
