package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.asserts.AbstractAssert;
import com.dat3m.dartagnan.asserts.AssertCompositeOr;
import com.dat3m.dartagnan.asserts.AssertInline;
import com.dat3m.dartagnan.asserts.AssertTrue;
import com.dat3m.dartagnan.expression.INonDet;
import com.dat3m.dartagnan.program.event.Local;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.Memory;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.program.utils.ThreadCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.Context;

import java.util.*;
import java.util.stream.*;

public class Program {

	private String name;
	private AbstractAssert ass;
	private AbstractAssert assFilter;
	private List<Thread> threads;
	private final Set<Location> locations;
	private Memory memory;
	private Arch arch;
	private ThreadCache cache;
	private boolean isUnrolled;
	private boolean isCompiled;

	public Program(Memory memory, Set<Location> locations) {
		this("", memory, locations);
	}

	public Program(String name, Memory memory, Set<Location> locations) {
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

	public Set<Location> getLocations() {
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

	/**
	 * For all events, determine what conditions must apply for that event to be executed.
	 * @param context
	 * Builder of expressions.
	 */
	public void encodeCF(EncodeContext context) {
		memory.encode(context);
		threads.forEach(t->t.encodeCF(context));
	}

	/**
	 * For all registers, specify their final value.
	 * Proposes that each final register value is the result of the last executed event that writes to it.
	 * @param context
	 * Builder of expressions.
	 */
	public void encodeFinalRegisterValues(EncodeContext context) {
		getCache().getRegWriterMap().entrySet().stream()
			.flatMap(e->IntStream.range(0, e.getValue().size())
				.mapToObj(i->context.or(
					context.not(context.exec((Event)e.getValue().get(i))),
					context.or(IntStream.range(i, e.getValue().size()).mapToObj(e.getValue()::get).map(Event.class::cast).map(context::exec)),
					context.eq(e.getKey().getLastValueExpr(context), e.getValue().get(i).getResultRegisterExpr(context)))))
			.forEach(context::rule);
	}

	/**
	 * When unrolling a cyclic program, one may consider checking whether or not all executions are contained in the unrolling.
	 * Proposes that no execution leads to any state exceeding the current unrolling.
	 * @param context
	 * Builder of expressions.
	 */
	public void encodeNoBoundEventExec(EncodeContext context) {
		getCache().getEvents(FilterBasic.get(EType.BOUND)).stream().map(context::exec).map(context::not).forEach(context::rule);
	}

	/**
	 * When unrolling a cyclic program, one may consider checking whether or not all executions are contained in the unrolling.
	 * Proposes that executions lead to some state exceeding the current unrolling.
	 * @param context
	 * Builder of expressions.
	 */
	public void encodeSomeBoundEventExec(EncodeContext context) {
		context.rule(context.or(getCache().getEvents(FilterBasic.get(EType.BOUND)).stream().map(context::exec)));
	}

	/**
	 * Proposes that all values chosen non-deterministically comply to the respective range of values.
	 * @param context
	 * Builder of expressions.
	 */
	public void encodeUINonDet(EncodeContext context) {
		Context ctx = context.context;
		getCache().getEvents(FilterBasic.get(EType.LOCAL)).stream()
			.filter(Local.class::isInstance).map(Local.class::cast)
			.flatMap(e->Stream.of(e.getExpr())
				.filter(INonDet.class::isInstance).map(INonDet.class::cast)
				.map(x->context.and(
					ctx.mkGe(x.toZ3Int(e, context), ctx.mkInt(x.getMin())),
					ctx.mkLe(x.toZ3Int(e, context), ctx.mkInt(x.getMax())))))
			.forEach(context::rule);
	}
}