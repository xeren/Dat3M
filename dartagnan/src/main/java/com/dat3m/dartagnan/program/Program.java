package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.program.utils.ThreadCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.asserts.AbstractAssert;
import com.dat3m.dartagnan.asserts.AssertCompositeOr;
import com.dat3m.dartagnan.asserts.AssertInline;
import com.dat3m.dartagnan.asserts.AssertTrue;
import com.dat3m.dartagnan.expression.INonDet;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Local;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.Memory;

import java.util.*;
import java.util.stream.*;

public class Program {

	private String name;
	private AbstractAssert ass;
	private AbstractAssert assFilter;
	private List<Thread> threads;
	private final ImmutableSet<Location> locations;
	private Memory memory;
	private Arch arch;
	private ThreadCache cache;
	private boolean isUnrolled;
	private boolean isCompiled;

	public Program(Memory memory, ImmutableSet<Location> locations) {
		this("", memory, locations);
	}

	public Program(String name, Memory memory, ImmutableSet<Location> locations) {
		this.name = name;
		this.memory = memory;
		this.locations = locations;
		this.threads = new ArrayList<>();
	}

	public boolean isCompiled() {
		return isCompiled;
	}

	public boolean isUnrolled() {
		return isUnrolled;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setArch(Arch arch) {
		this.arch = arch;
	}

	public Arch getArch() {
		return arch;
	}

	public Memory getMemory() {
		return this.memory;
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

	public void clearCache() {
		for(Thread t: threads) {
			t.clearCache();
		}
	}

	public List<Thread> getThreads() {
		return threads;
	}

	public ImmutableSet<Location> getLocations() {
		return locations;
	}

	public List<Event> getEvents() {
		List<Event> events = new ArrayList<>();
		for(Thread t: threads) {
			events.addAll(t.getCache().getEvents(FilterBasic.get(EType.ANY)));
		}
		return events;
	}

	public AbstractAssert createAssertion() {
		AbstractAssert ass = new AssertTrue();
		List<Event> assertions = new ArrayList<>();
		for(Thread t: threads) {
			assertions.addAll(t.getCache().getEvents(FilterBasic.get(EType.ASSERTION)));
		}
		if(!assertions.isEmpty()) {
			ass = new AssertInline((Local) assertions.get(0));
			for(int i = 1; i < assertions.size(); i++) {
				ass = new AssertCompositeOr(ass, new AssertInline((Local) assertions.get(i)));
			}
		}
		return ass;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	public int unroll(int bound, int nextId) {
		for(Thread thread: threads) {
			nextId = thread.unroll(bound, nextId);
		}
		isUnrolled = true;
		cache = null;
		return nextId;
	}


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	public int compile(Arch target, int nextId) {
		for(Thread thread: threads) {
			nextId = thread.compile(target, nextId);
		}
		isCompiled = true;
		cache = null;
		return nextId;
	}


	// Encoding
	// -----------------------------------------------------------------------------------------------------------------

	public BoolExpr encodeCF(EncodeContext context) {
		for(Event e: getEvents()) {
			e.initialise(context);
		}
		return context.and(memory.encode(context), context.and(threads.stream().map(t->t.encodeCF(context))));
	}

	/**
	 * For all registers, specify their final value.
	 * @param context
	 * Builder of expressions.
	 * @return
	 * Proposition that each final register value is the result of the last executed event that writes to it.
	 */
	public BoolExpr encodeFinalRegisterValues(EncodeContext context) {
		return context.and(getCache().getRegWriterMap().entrySet().stream()
			.flatMap(e->IntStream.range(0, e.getValue().size())
				.mapToObj(i->context.or(
					context.not(e.getValue().get(i).exec()),
					context.or(IntStream.range(i, e.getValue().size()).mapToObj(e.getValue()::get).map(Event::exec)),
					context.eq(e.getKey().getLastValueExpr(context), ((RegWriter) e.getValue().get(i)).getResultRegisterExpr())))));
	}

	/**
	 * @param context
	 * Builder of expressions.
	 * @return
	 * Proposition that none of the bound events is reached.
	 */
	public BoolExpr encodeNoBoundEventExec(EncodeContext context) {
		return context.not(context.or(getCache().getEvents(FilterBasic.get(EType.BOUND)).stream().map(Event::exec)));
	}

	/**
	 * @param context
	 * Builder of expressions.
	 * @return
	 * Proposition that all values chosen non-deterministically comply to the respective range of values.
	 */
	public BoolExpr encodeUINonDet(EncodeContext context) {
		Context ctx = context.context;
		return context.and(getCache().getEvents(FilterBasic.get(EType.LOCAL)).stream()
			.filter(Local.class::isInstance).map(Local.class::cast)
			.flatMap(e->Stream.of(e.getExpr())
				.filter(INonDet.class::isInstance).map(INonDet.class::cast)
				.map(x->context.and(
					ctx.mkGe(x.toZ3Int(e, context), ctx.mkInt(x.getMin())),
					ctx.mkLe(x.toZ3Int(e, context), ctx.mkInt(x.getMax()))))));
	}

	public BoolExpr getRf(EncodeContext context, Model model) {
		List<Event> write = getCache().getEvents(FilterBasic.get(EType.WRITE));
		return context.and(getCache().getEvents(FilterBasic.get(EType.READ)).stream()
			.flatMap(r->write.stream()
				.map(w->context.edge("rf", w, r)))
			.filter(e->model.getConstInterp(e) != null && model.getConstInterp(e).isTrue()));
	}
}