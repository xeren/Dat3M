package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.BoolExpr;

import java.util.*;

/**
Collects events that are always passed through together.
*/
public final class ControlBlock {

	private final ArrayList<Event> event = new ArrayList<>();

	private final ControlBlock in;
	private final ControlBlock alternative;
	private final ControlBlock[] joined;
	public final BoolExpr variable;

	/**
	Creates a new control block.
	@param parent
	Directly implied by this, contains the conditional branch that issues this creation.
	@param var
	Proposition that this block is passed through in the modelled run.
	*/
	public ControlBlock(ControlBlock parent, BoolExpr var) {
		//TODO !var.isFalse()
		assert null!=var;
		assert null==parent == var.isTrue();
		in = parent;
		alternative = null;
		joined = null;
		variable = var;
	}

	/**
	Creates the control block for an else statement.
	TODO whenever this and the alternative rejoin in some block, that block is also implied by parent.
	@param parent
	Directly implied by this, contains the conditional branch that issues this creation.
	@param alt
	Mutually exclusive block also created by the branching event.
	@param var
	Proposition that this block is passed through in the modelled run.
	*/
	public ControlBlock(ControlBlock parent, ControlBlock alt, BoolExpr var) {
		//TODO !var.isFalse()
		assert null!=var && !var.isTrue();
		assert null!=parent;
		assert null!=alt && alt.in==parent;
		in = parent;
		alternative = alt;
		joined = null;
		variable = var;
	}

	private ControlBlock(ControlBlock[] parent, BoolExpr var) {
		assert null!=var && !var.isTrue() && !var.isFalse();
		assert null!=parent && 0<parent.length && java.util.Arrays.stream(parent).allMatch(p->null!=p && null==p.joined);
		in = null;
		alternative = null;
		joined = parent;
		variable = var;
	}

	/**
	Declares an event as part of this control block.
	*/
	public void add(Event e) {
		event.add(e);
	}

	/**
	Creates a new control block if necessary.
	@param var
	Variable used in creation.
	@param jump
	Collection of control blocks ending in the label.
	@return
	Reused or new control block.
	*/
	public static ControlBlock join(BoolExpr var, Collection<ControlBlock> jump) {
		assert !jump.isEmpty();
		HashSet<ControlBlock> t = new HashSet<>();
		LinkedList<ControlBlock> r = new LinkedList<>();
		for(ControlBlock j : jump)
			(null==j.alternative ? t : r).add(j);
		Iterator<ControlBlock> i = r.iterator();
		while(i.hasNext()) {
			ControlBlock b = i.next();
			if(t.remove(b.alternative)) {
				i.remove();
				(null==b.in.alternative ? t : r).add(b.in);
				i = r.iterator();
			}
		}
		r.addAll(t);
		assert !r.isEmpty();
		return 1 == r.size() ? r.getFirst() : new ControlBlock(r.toArray(new ControlBlock[0]),var);
	}
}
