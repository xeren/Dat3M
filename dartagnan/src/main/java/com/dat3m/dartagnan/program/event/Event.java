package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;

public abstract class Event implements Comparable<Event> {

	public static final int PRINT_PAD_EXTRA = 50;

	protected int oId = -1; // ID after parsing (original)
	protected int uId = -1; // ID after unrolling
	protected int cId = -1; // ID after compilation

	protected final Set<String> filter;

	protected transient Event successor;

	protected transient BoolExpr cfEnc;
	protected transient BoolExpr cfCond;

	protected transient BoolExpr cfVar;
	protected transient BoolExpr execVar;

	protected transient Set<Event> listeners = new HashSet<>();

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

	public void addListener(Event e) {
		listeners.add(e);
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
	 * Called before encoding.
	 * Defines constants for execution and control flow inclusion of this event.
	 * @param ctx
	 * Reasoning context to create predefined constants.
	 * @throws RuntimeException
	 * The event has not been assigned a compilation ID.
	 */
	public void initialise(EncodeContext c){
		if(cId < 0){
			throw new RuntimeException("Event ID is not set in " + this);
		}
		Context ctx = c.context;
		//execVar = ctx.mkBoolConst("exec(" + repr() + ")");
		//cfVar = ctx.mkBoolConst("cf(" + repr() + ")");
		execVar = (BoolExpr)ctx.mkFuncDecl("exec", ctx.mkIntSort(), ctx.mkBoolSort()).apply(ctx.mkInt(cId));
		cfVar = (BoolExpr)ctx.mkFuncDecl("cf", ctx.mkIntSort(), ctx.mkBoolSort()).apply(ctx.mkInt(cId));
	}

	/**
	 * Identifies this event in a formula.
	 */
	public String repr() {
		return "E" + cId;
	}

	/**
	 * In a directed acyclic graph, each thread has to choose some path from start to end.
	 * @return
	 * Proposition that this event contained in a modelled execution.
	 */
	public BoolExpr exec(){
		return execVar;
	}

	/**
	 * The control flow of a program is a graph deciding which statements of the program should be executed.
	 * @return
	 * Proposition that this event is included in the execution's control flow.
	 */
	public BoolExpr cf(){
		return cfVar;
	}

	/**
	 * The control flow graph can join in any point.
	 * It must reach a predecessor for which there may be several candidates.
	 * @param context
	 * Manager for expressions.
	 * @param cond
	 * Another condition that implies the control flow reaching this event.
	 * @see #encodeCF(EncodeContext, BoolExpr)
	 */
	public void addCfCond(EncodeContext context, BoolExpr cond){
		cfCond = cfCond == null ? cond : context.or(cfCond, cond);
	}

	/**
	 * Accumulates the conditions previously passed to {@link #addCfCond(EncodeContext, BoolExpr)}.
	 * Finalizes the conditions: repeated calls will ignore parameters and return the same value.
	 * @param context
	 * Manager for expressions.
	 * @param cond
	 * Another condition that implies the control flow reaching this event.
	 * @return
	 * Proposition that this event is reached iff any of its preconditions is met.
	 */
	public BoolExpr encodeCF(EncodeContext context, BoolExpr cond) {
		if(cfEnc == null){
			cfCond = cfCond == null ? cond : context.or(cfCond, cond);
			cfEnc = context.and(
				context.eq(cfVar, cfCond),
				encodeExec(context),
				successor == null ? context.and() : successor.encodeCF(context, cfVar));
		}
		return cfEnc;
	}

	/**
	 * Binds conditions to the execution of this event.
	 * Used in {@link #encodeCF(EncodeContext, BoolExpr)}.
	 * Usually the control flow suffices.
	 * @param context
	 * Reasoning context and expression factory.
	 * @return
	 * Proposition that this event is executed iff specific conditions are satisfied.
	 */
	protected BoolExpr encodeExec(EncodeContext context){
		return context.eq(execVar, cfVar);
	}
}