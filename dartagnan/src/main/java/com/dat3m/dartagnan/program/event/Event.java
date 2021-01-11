package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;

public abstract class Event implements Comparable<Event> {

	public static final int PRINT_PAD_EXTRA = 50;

	protected int oId = -1;        // ID after parsing (original)
	protected int uId = -1;        // ID after unrolling
	protected int cId = -1;        // ID after compilation

	protected int cLine = -1;    // line in the original C program

	protected final Set<String> filter;

	protected transient Event successor;

	protected transient BoolExpr cfEnc;
	protected transient BoolExpr cfCond;

	protected transient BoolExpr cfVar;
	protected transient BoolExpr execVar;

	protected Event() {
		filter = new HashSet<>();
	}

	protected Event(Event other) {
		this.oId = other.oId;
		this.uId = other.uId;
		this.cId = other.cId;
		this.cLine = other.cLine;
		this.filter = other.filter;
	}

	public int getOId() {
		return oId;
	}

	public void setOId(int id) {
		this.oId = id;
	}

	public int getUId() {
		return uId;
	}

	public int getCId() {
		return cId;
	}

	public int getCLine() {
		return cLine;
	}

	public void setCLine(int line) {
		this.cLine = line;
	}

	@Deprecated
	public Event getSuccessor() {
		return successor;
	}

	@Deprecated
	public void setSuccessor(Event event) {
		successor = event;
	}

	@Deprecated
	public LinkedList<Event> getSuccessors() {
		LinkedList<Event> result = successor != null
			? successor.getSuccessors()
			: new LinkedList<>();
		result.addFirst(this);
		return result;
	}

	public String label() {
		return repr() + " " + getClass().getSimpleName();
	}

	public boolean is(String param) {
		return param != null && (filter.contains(param));
	}

	public void addFilters(String... params) {
		filter.addAll(Arrays.asList(params));
	}

	public boolean hasFilter(String f) {
		return filter.contains(f);
	}

	@Override
	public int compareTo(Event e) {
		int result = Integer.compare(cId, e.cId);
		if(result == 0) {
			result = Integer.compare(uId, e.uId);
			if(result == 0) {
				result = Integer.compare(oId, e.oId);
			}
		}
		return result;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	public static void setUId(Event[] event, int nextId) {
		for(Event e : event)
			e.uId = nextId++;
	}

	public Event getCopy() {
		throw new UnsupportedOperationException("Copying is not allowed for " + getClass().getSimpleName());
	}


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	public int compile(Arch target, int nextId, Event predecessor) {
		cId = nextId++;
		if(successor != null) {
			return successor.compile(target, nextId, this);
		}
		return nextId;
	}

	protected int compileSequence(Arch target, int nextId, Event predecessor, LinkedList<Event> sequence) {
		for(Event e : sequence) {
			e.oId = oId;
			e.uId = uId;
			e.cId = nextId++;
			predecessor.setSuccessor(e);
			predecessor = e;
		}
		if(successor != null) {
			predecessor.successor = successor;
			return successor.compile(target, nextId, predecessor);
		}
		return nextId;
	}


	// Encoding
	// -----------------------------------------------------------------------------------------------------------------

	public void initialise(Context ctx) {
		if(cId < 0) {
			throw new RuntimeException("Event ID is not set in " + this);
		}
		execVar = ctx.mkBoolConst("exec(" + repr() + ")");
		cfVar = ctx.mkBoolConst("cf(" + repr() + ")");
	}

	public String repr() {
		return "E" + cId;
	}

	public BoolExpr exec() {
		return execVar;
	}

	public BoolExpr cf() {
		return cfVar;
	}

	public void addCfCond(Context ctx, BoolExpr cond) {
		cfCond = (cfCond == null) ? cond : ctx.mkOr(cfCond, cond);
	}

	public BoolExpr encodeCF(Context ctx, BoolExpr cond) {
		if(cfEnc == null) {
			cfCond = (cfCond == null) ? cond : ctx.mkOr(cfCond, cond);
			cfEnc = ctx.mkEq(cfVar, cfCond);
			cfEnc = ctx.mkAnd(cfEnc, encodeExec(ctx));
			if(successor != null) {
				cfEnc = ctx.mkAnd(cfEnc, successor.encodeCF(ctx, cfVar));
			}
		}
		return cfEnc;
	}

	protected BoolExpr encodeExec(Context ctx) {
		return ctx.mkEq(execVar, cfVar);
	}
}