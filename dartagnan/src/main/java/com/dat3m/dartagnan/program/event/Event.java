package com.dat3m.dartagnan.program.event;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;

public abstract class Event implements Comparable<Event> {

	private int uId = -1;        // ID after unrolling

	private int cLine = -1;    // line in the original C program

	protected final Set<String> filter;

	private transient BoolExpr execVar;

	protected Event() {
		filter = new HashSet<>();
	}

	protected Event(Event other) {
		this.uId = other.uId;
		this.cLine = other.cLine;
		this.filter = other.filter;
	}

	public int getCId() {
		return uId;
	}

	public int getCLine() {
		return cLine;
	}

	public void setCLine(int line) {
		this.cLine = line;
	}

	protected String label() {
		return " ";
	}

	public boolean is(String param) {
		return param != null && filter.contains(param);
	}

	public void addFilters(String... params) {
		filter.addAll(Arrays.asList(params));
	}

	public boolean hasFilter(String f) {
		return filter.contains(f);
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	public void setUId(int id) {
		uId = id;
	}

	public Event getCopy() {
		throw new UnsupportedOperationException("Copying is not allowed for " + getClass().getSimpleName());
	}


	// Encoding
	// -----------------------------------------------------------------------------------------------------------------

	public String repr() {
		return "E" + uId;
	}

	public BoolExpr exec() {
		return execVar;
	}

	public interface RuleAcceptor {
		void add(BoolExpr rule);
	}

	/**
	Prepares this event to be represented in a boolean formula.
	@param context
	Builder for expressions.
	@param out
	Receives additional rules.
	@param in
	Proposition that control flows to this event.
	*/
	public void encode(Context context, RuleAcceptor out, BoolExpr in) {
		execVar = in;
	}

	@Override
	public int compareTo(Event e) {
		return Integer.compare(uId, e.uId);
	}

	@Override
	public int hashCode() {
		return uId;
	}

	@Override
	public String toString() {
		return uId + " " + getClass().getSimpleName() + label() + filter;
	}
}