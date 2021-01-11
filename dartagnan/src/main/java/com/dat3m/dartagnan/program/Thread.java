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
	*/
	public void unroll(int bound) {
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

	public void compile(Arch target) {
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

	public BoolExpr encodeCF(Context ctx) {
		return compiled[0].encodeCF(ctx, ctx.mkTrue());
	}
}
