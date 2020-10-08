package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.BoolExpr;
import java.util.*;

public abstract class Event implements Comparable<Event> {

	public static final int PRINT_PAD_EXTRA = 50;

	protected int oId = -1; // ID after parsing (original)
	protected int uId = -1; // ID after unrolling
	protected int cId = -1; // ID after compilation

	protected final Set<String> filter;

	protected transient Event successor;

	protected Event(){
		filter = new HashSet<>();
	}

	protected Event(Event other){
		this.oId = other.oId;
		this.uId = other.uId;
		this.cId = other.cId;
		this.filter = other.filter;
	}

	public int getOId() {
		return oId;
	}

	public void setOId(int id) {
		this.oId = id;
	}

	public int getUId(){
		return uId;
	}

	public int getCId() {
		return cId;
	}

	/**
	 * Total order over all events of a thread as they occurred in the program code.
	 * @return
	 * The next event according to the program code.
	 */
	public Event getSuccessor(){
		return successor;
	}

	/**
	 * @param event
	 * New successor event for this.
	 */
	public void setSuccessor(Event event){
		successor = event;
	}

	/**
	 * @return
	 * List of this event and all successors, in order of appearance.
	 */
	public LinkedList<Event> getSuccessors(){
		LinkedList<Event> result = successor != null
				? successor.getSuccessors()
				: new LinkedList<>();
		result.addFirst(this);
		return result;
	}

	/**
	 * @return
	 * Caption as to display in a counter example graph.
	 */
	public String label(){
		return repr() + " " + getClass().getSimpleName();
	}

	public boolean is(String param){
		return param != null && (filter.contains(param));
	}

	public void addFilters(String... params){
		filter.addAll(Arrays.asList(params));
	}

	@Override
	public int compareTo(Event e){
		int result = Integer.compare(cId, e.cId);
		if(result == 0){
			result = Integer.compare(uId, e.uId);
			if(result == 0){
				result = Integer.compare(oId, e.oId);
			}
		}
		return result;
	}

	public void notify(Event e) {
		throw new UnsupportedOperationException("notify is not allowed for " + getClass().getSimpleName());
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Recursively updates the Unroll Identifier of this event and all its successors.
	 * Individual identifiers for successors are incremented.
	 * @param nextId
	 * New unroll identifier for this event.
	 * @return
	 * First unassigned identifier.
	 */
	public final int setUId(int nextId) {
		for(Event e = this; null != e; e = e.successor)
			e.uId = nextId++;
	  return nextId;
	}

	/**
	 * Transforms a recursive program into a finite program.
	 * @param bound
	 * Maximal number of iteration for a loop.
	 * @param predecessor
	 * Latest unrolled event.
	 */
	public void unroll(int bound, Event predecessor) {
		Event copy = this;
		if(predecessor != null) {
			// This check must be done inside this if
			// Needed for the current implementation of copy in If events
			if(bound != 1)
				copy = getCopy();
			predecessor.setSuccessor(copy);
		}
		if(successor != null)
			successor.unroll(bound, copy);
	}

	/**
	 * Creates a copy of this.
	 * Used during unrolling.
	 */
	public Event getCopy(){
		throw new UnsupportedOperationException("Copying is not allowed for " + getClass().getSimpleName());
	}

	static Event copyPath(Event from, Event until, Event appendTo){
		while(from != null && !from.equals(until)){
			Event copy = from.getCopy();
			appendTo.setSuccessor(copy);
			if(from instanceof If){
				from = ((If)from).getExitElseBranch();
				appendTo = ((If)copy).getExitElseBranch();
			} else if(from instanceof While){
				from = ((While)from).getExitEvent();
				appendTo = ((While)copy).getExitEvent();
			} else {
				appendTo = copy;
			}
			from = from.successor;
		}
		return appendTo;
	}


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Recursively compiles this instruction and all its successors into events.
	 * @param target
	 * Instruction set to compile to.
	 * @param nextId
	 * Current latest compilation ID.
	 * @param predecessor
	 * Current latest compiled event.
	 * @return
	 * New latest compilation ID.
	 */
	public int compile(Arch target, int nextId, Event predecessor) {
		cId = nextId++;
		if(successor != null){
			return successor.compile(target, nextId, this);
		}
		return nextId;
	}

	/**
	 * Given a sequence of new events, inserts those events into the list of compiled events.
	 * Assigns new compilation IDs to all those events.
	 * Continues compilation at this successor.
	 * Provided for {@link #compile}.
	 * @param target
	 * Architecture to be passed to the successor's compilation.
	 * @param nextId
	 * Current latest compilation ID.
	 * @param predecessor
	 * Current latest compiled event.
	 * @param sequence
	 * Finite list of new events.
	 * @return
	 * New latest compilation ID.
	 */
	protected int compileSequence(Arch target, int nextId, Event predecessor, LinkedList<Event> sequence){
		for(Event e : sequence){
			e.oId = oId;
			e.uId = uId;
			e.cId = nextId++;
			predecessor.setSuccessor(e);
			predecessor = e;
		}
		if(successor != null){
			predecessor.successor = successor;
			return successor.compile(target, nextId, predecessor);
		}
		return nextId;
	}


	// Encoding
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Identifies this event in a formula.
	 */
	public String repr() {
		return "E" + cId;
	}

	/**
	 * States the rule that this event is reached iff any of its preconditions is met.
	 * @param context
	 * Manager for expressions.
	 * @param cond
	 * Another condition that implies the control flow reaching this event.
	 */
	public void encodeCF(EncodeContext context, BoolExpr cond) {
		context.rule(context.eq(context.cf(this), context.condition(this)));
		encodeExec(context);
		if(null != successor)
			successor.encodeCF(context, context.cf(this));
	}

	/**
	 * Proposes that this event is executed iff specific conditions are satisfied.
	 * Binds conditions to the execution of this event.
	 * Used in {@link #encodeCF(EncodeContext, BoolExpr)}.
	 * Usually the control flow suffices.
	 * @param context
	 * Reasoning context and expression factory.
	 */
	protected void encodeExec(EncodeContext context){
		context.rule(context.eq(context.exec(this), context.cf(this)));
	}
}