package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.program.ControlBlock;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;

public abstract class Event implements Comparable<Event> {

	public static final int PRINT_PAD_EXTRA = 50;

	protected int oId = -1;		// ID after parsing (original)
	protected int uId = -1;		// ID after unrolling
	protected int cId = -1;		// ID after compilation
	
	protected int cLine = -1;	// line in the original C program

	protected final Set<String> filter;

	protected transient Event successor;

	protected transient ControlBlock control;

	protected Set<Event> listeners = new HashSet<>();
	
	protected Event(){
		filter = new HashSet<>();
	}

	protected Event(Event other){
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

	public int getUId(){
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

	public Event getSuccessor(){
		return successor;
	}

	public void setSuccessor(Event event){
		successor = event;
	}

	public LinkedList<Event> getSuccessors(){
		LinkedList<Event> result = successor != null
				? successor.getSuccessors()
				: new LinkedList<>();
		result.addFirst(this);
		return result;
	}

	public String label(){
		return repr() + " " + getClass().getSimpleName();
	}

	public boolean is(String param){
		return param != null && (filter.contains(param));
	}

	public void addFilters(String... params){
		filter.addAll(Arrays.asList(params));
	}

	public boolean hasFilter(String f) {
		return filter.contains(f);
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

    public int setUId(int nextId) {
    	uId = nextId++;
    	if(successor != null) {
    		nextId = successor.setUId(nextId);
    	}
	    return nextId;
    }

    public void unroll(int bound, Event predecessor) {
    	Event copy = this;
    	if(predecessor != null) {
    		// This check must be done inside this if
    		// Needed for the current implementation of copy in If events
    		if(bound != 1) {
        		copy = getCopy();    			
    		}
    		predecessor.setSuccessor(copy);
    	}
    	if(successor != null) {
    		successor.unroll(bound, copy);
    	}
	    return;
    }

	public Event getCopy(){
		throw new UnsupportedOperationException("Copying is not allowed for " + getClass().getSimpleName());
	}

	static Event copyPath(Event from, Event until, Event appendTo){
		while(from != null && !from.equals(until)){
			Event copy = from.getCopy();
			appendTo.setSuccessor(copy);
			appendTo = copy;
			from = from.successor;
		}
		return appendTo;
	}


    // Compilation
    // -----------------------------------------------------------------------------------------------------------------

    public int compile(Arch target, int nextId, Event predecessor) {
		cId = nextId++;
		if(successor != null){
			return successor.compile(target, nextId, this);
		}
        return nextId;
    }

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

	@FunctionalInterface
	public interface ControlMessage {
		void send(int destination, ControlBlock content);
	}

	public ControlBlock initialise(Context ctx, ControlBlock ctrl, ControlMessage message){
		assert 0 <= cId;
		control = ctrl;
		return ctrl;
	}

	public String repr() {
		return "E" + cId;
	}

	public BoolExpr exec(){
		assert null != control;
		return control.variable;
	}

	public final ControlBlock cf(){
		return control;
	}

	/**
	Proposes that both passed events are executed.
	May take into account if their control variables coincide, imply one another or exclude each other.
	@param context
	Builder for expressions.
	@param other
	Another event of the program.
	@return
	Proposition that the modelled execution include both events.
	False proposition, if both events exclude each other.
	*/
	public final BoolExpr exec(Context context, Event other) {
		if(excludes(other))
			return context.mkFalse();
		if(implies(other))
			return exec();
		if(other.implies(this))
			return other.exec();
		return context.mkAnd(exec(),other.exec());
	}

	/**
	Checks if executions may include this event but not another event.
	<p>
	Currently only the program's control flow contributes to this property.
	However, events on any thread's entry block possess few conditions.
	@param other
	Another event of the program.
	@return
	Proofed that all executions that execute this program must also execute {@code other}.
	Otherwise unclear.
	*/
	public final boolean implies(Event other) {
		return other.control.variable==other.exec() && (control==other.control || control.implies(other.control));
	}

	/**
	Checks if this and another event may occur in the same execution.
	<p>
	Currently only control flow is used, therefore only internal pairs may be exclusive.
	This is a symmetry: {@code a.excludes(b) == b.excludes(a)}
	@param other
	Second event of the program.
	@return
	Proofed that no execution executes both events.
	Otherwise unclear.
	*/
	public final boolean excludes(Event other) {
		return control.excludes(other.control) || other.control.excludes(control);
	}

	@FunctionalInterface
	public interface Constraint{
		void add(BoolExpr constraint);
	}

	public void encode(Context context, Constraint out){
	}
}