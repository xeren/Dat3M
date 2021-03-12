package com.dat3m.dartagnan.program;

import com.microsoft.z3.BoolExpr;
import java.util.*;

/**
Collects events that are always passed through together.
*/
public final class ControlBlock {

	private final ControlBlock in;
	private ControlBlock out;
	private final ControlBlock alternative;
	private final ControlBlock[] joined;
	public final BoolExpr variable;
	private final HashSet<ControlBlock> implied = new HashSet<>();
	private final HashSet<ControlBlock> excluded = new HashSet<>();

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
		for(ControlBlock p : parent){
			assert null==p.out;
			p.out = this;
		}
	}

	public boolean implies(ControlBlock other) {
		initialise();
		return implied.contains(other);
	}

	public boolean excludes(ControlBlock other) {
		initialise();
		other.initialise();
		return !Collections.disjoint(excluded,other.implied);
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
			joinAdd(t,r,j);
		Iterator<ControlBlock> i = r.iterator();
		while(i.hasNext()) {
			ControlBlock b = i.next();
			if(t.remove(b.alternative)) {
				i.remove();
				joinAdd(t,r,b.in);
				i = r.iterator();
			}
		}
		r.addAll(t);
		assert !r.isEmpty();
		return 1 == r.size() ? r.getFirst() : new ControlBlock(r.toArray(new ControlBlock[0]),var);
	}

	private static void joinAdd(HashSet<ControlBlock> t, LinkedList<ControlBlock> r, ControlBlock b){
		assert null==b.joined || Arrays.stream(b.joined).allMatch(jj->null==jj.joined);
		for(ControlBlock j : null!=b.joined ? b.joined : new ControlBlock[]{b})
			(null==j.alternative ? t : r).add(j);
	}

	private void initialise() {
		if(!implied.isEmpty())
			return;
		if(null!=alternative)
			excluded.add(alternative);
		if(null!=in){
			implied.add(in);
			in.initialise();
			implied.addAll(in.implied);
			excluded.addAll(in.excluded);
		}
		if(null!=out){
			implied.add(out);
			out.initialise();
			implied.addAll(out.implied);
			excluded.addAll(out.excluded);
		}
	}
}
