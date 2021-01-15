package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.asserts.AbstractAssert;
import com.dat3m.dartagnan.asserts.AssertCompositeOr;
import com.dat3m.dartagnan.asserts.AssertInline;
import com.dat3m.dartagnan.asserts.AssertTrue;
import com.dat3m.dartagnan.parsers.program.Arch;
import com.dat3m.dartagnan.program.event.BoundEvent;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Local;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.Memory;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.program.utils.ThreadCache;
import com.dat3m.dartagnan.wmm.Filter;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;

public class Program {

	private AbstractAssert ass;
	private AbstractAssert assFilter;
	private final List<Thread> threads;
	private final ImmutableSet<Location> locations;
	private final Memory memory;
	private Arch arch;
	private ThreadCache cache;

	public Program(Memory memory, ImmutableSet<Location> locations) {
		this.memory = memory;
		this.locations = locations;
		this.threads = new ArrayList<>();
	}

	public void setArch(Arch arch) {
		this.arch = arch;
	}

	public Arch getArch() {
		return arch;
	}

	public Memory getMemory() {
		return memory;
	}

	public AbstractAssert getAss() {
		return ass;
	}

	public void setAss(AbstractAssert ass) {
		this.ass = ass;
	}

	public AbstractAssert getAssFilter() {
		return assFilter;
	}

	public void setAssFilter(AbstractAssert ass) {
		this.assFilter = ass;
	}

	public void add(Thread t) {
		threads.add(t);
	}

	public ThreadCache getCache() {
		if(cache == null) {
			cache = new ThreadCache(getEvents());
		}
		return cache;
	}

	public List<Thread> getThreads() {
		return threads;
	}

	public ImmutableSet<Location> getLocations() {
		return locations;
	}

	public List<Event> getEvents() {
		List<Event> events = new ArrayList<>();
		for(Thread t : threads)
			events.addAll(Arrays.asList(t.compiled));
		return events;
	}

	public void updateAssertion() {
		if(ass != null) {
			return;
		}
		List<Event> assertions = new ArrayList<>();
		for(Thread t : threads) {
			assertions.addAll(t.getCache().getEvents(Filter.of(EType.ASSERTION)));
		}
		ass = new AssertTrue();
		if(!assertions.isEmpty()) {
			ass = new AssertInline((Local) assertions.get(0));
			for(int i = 1; i < assertions.size(); i++) {
				ass = new AssertCompositeOr(ass, new AssertInline((Local) assertions.get(i)));
			}
		}
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	public int unroll(int bound, int nextId) {
		for(Thread thread : threads) {
			thread.unroll(bound);
			for(Event event : thread.unrolled)
				event.setUId(nextId++);
		}
		cache = null;
		return nextId;
	}


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	public int compile(Arch target, int nextId) {
		for(Thread thread : threads) {
			thread.compile(target);
			Event.setCId(thread.compiled, nextId);
			nextId += thread.compiled.length;
		}
		cache = null;
		return nextId;
	}


	// Encoding
	// -----------------------------------------------------------------------------------------------------------------

	public BoolExpr encodeCF(Context ctx) {
		BoolExpr enc = memory.encode(ctx);
		for(Thread t : threads) {
			enc = ctx.mkAnd(enc, t.encodeCF(ctx));
		}
		return enc;
	}

	public BoolExpr encodeFinalRegisterValues(Context ctx) {
		Map<Register,ArrayList<RegWriter>> eMap = new HashMap<>();
		for(RegWriter e : getCache().getEvents(RegWriter.class))
			eMap.computeIfAbsent(e.getResultRegister(), k->new ArrayList<>()).add(e);
		LinkedList<BoolExpr> enc = new LinkedList<>();
		for(Register reg : eMap.keySet()) {
			ArrayList<RegWriter> events = eMap.get(reg);
			events.sort(Collections.reverseOrder());
			for(int i = 0; i < events.size(); i++) {
				LinkedList<BoolExpr> lastModReg = new LinkedList<>();
				lastModReg.add(ctx.mkNot(((Event)eMap.get(reg).get(i)).exec()));
				for(int j = 0; j < i; j++)
					lastModReg.add(((Event)events.get(j)).exec());
				lastModReg.add(ctx.mkEq(reg.getLastValueExpr(ctx), events.get(i).getResultRegisterExpr()));
				enc.add(ctx.mkOr(lastModReg.toArray(new BoolExpr[0])));
			}
		}
		return ctx.mkAnd(enc.toArray(new BoolExpr[0]));
	}

	public BoolExpr encodeNoBoundEventExec(Context ctx) {
		LinkedList<BoolExpr> enc = new LinkedList<>();
		for(Event e : getCache().getEvents(BoundEvent.class))
			enc.add(ctx.mkNot(e.exec()));
		return ctx.mkAnd(enc.toArray(new BoolExpr[0]));
	}
}