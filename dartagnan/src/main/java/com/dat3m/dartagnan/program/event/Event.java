package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.parsers.program.Arch;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;

public abstract class Event implements Comparable<Event> {

	protected int oId = -1;        // ID after parsing (original)
	protected int uId = -1;        // ID after unrolling
	protected int cId = -1;        // ID after compilation

	protected int cLine = -1;    // line in the original C program

	protected final Set<String> filter;

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


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	public static void setCId(Event[] event, int nextId) {
		for(Event e : event)
			e.cId = nextId++;
	}

	/**
	Given an architecture, simplify this event into smaller events defined by the architecture such that all guarantees are satisfied.
	@return
	Array of events to substitute.
	{@code null} if no substitution should take place.
	*/
	public Event[] compile(Arch target) {
		return null;
	}


	// Encoding
	// -----------------------------------------------------------------------------------------------------------------

	public String repr() {
		return "E" + cId;
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
		int result = Integer.compare(cId, e.cId);
		if(result == 0) {
			result = Integer.compare(uId, e.uId);
			if(result == 0) {
				result = Integer.compare(oId, e.oId);
			}
		}
		return result;
	}

	@Override
	public int hashCode() {
		return cId;
	}

	@Override
	public String toString() {
		return cId + " " + getClass().getSimpleName() + label() + filter;
	}
}