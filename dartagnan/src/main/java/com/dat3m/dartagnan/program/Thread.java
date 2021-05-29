package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.program.event.CondJump;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.If;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.utils.preprocessing.BranchReordering;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.program.utils.ThreadCache;
import com.dat3m.dartagnan.program.utils.preprocessing.DeadCodeElimination;
import com.dat3m.dartagnan.utils.equivalence.BranchEquivalence;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import java.util.*;

public class Thread {

	private final String name;
    private final int id;
    private final Event entry;
    private Event exit;

    private final Map<String, Register> registers;
    private ThreadCache cache;

    public Thread(String name, int id, Event entry){
        if(id < 0){
            throw new IllegalArgumentException("Invalid thread ID");
        }
        if(entry == null){
            throw new IllegalArgumentException("Thread entry event must be not null");
        }
        entry.setThread(this);
        this.name = name;
        this.id = id;
        this.entry = entry;
        this.exit = this.entry;
        this.registers = new HashMap<>();
    }

    public Thread(int id, Event entry){
    	this(String.valueOf(id), id, entry);
    }

    public String getName(){
        return name;
    }

    public int getId(){
        return id;
    }

    public ThreadCache getCache(){
        if(cache == null){
            cache = new ThreadCache(entry.getSuccessors());
        }
        return cache;
    }

    public List<Event> getEvents() {
        return getCache().getEvents(FilterBasic.get(EType.ANY));
    }

    public void clearCache(){
        cache = null;
    }

    public Register getRegister(String name){
        return registers.get(name);
    }

    public Register addRegister(String name, int precision){
        if(registers.containsKey(name)){
            throw new RuntimeException("Register " + id + ":" + name + " already exists");
        }
        cache = null;
        Register register = new Register(name, id, precision);
        registers.put(register.getName(), register);
        return register;
    }

    public Event getEntry(){
        return entry;
    }

    public Event getExit(){
        return exit;
    }

    public void append(Event event){
        exit.setSuccessor(event);
        event.setThread(this);
        updateExit(event);
        cache = null;
    }

    public void updateExit(Event event){
        exit = event;
        Event next = exit.getSuccessor();
        while(next != null){
            exit = next;
            exit.setThread(this);
            next = next.getSuccessor();
        }
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

        return id == ((Thread) obj).id;
    }

    public void simplify() {
        entry.simplify(null);
        cache = null;
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    public int unroll(int bound, int nextId){
    	while(bound > 0) {
    		entry.unroll(bound, null);
    		bound--;
    	}
        nextId = entry.setUId(nextId);
        updateExit(entry);
        cache = null;
        return nextId;
    }


    // Compilation
    // -----------------------------------------------------------------------------------------------------------------

    public int compile(Arch target, int nextId) {
        nextId = entry.compile(target, nextId, null);
        updateExit(entry);
        cache = null;
        return nextId;
    }

    // Encoding
    // -----------------------------------------------------------------------------------------------------------------

    public BoolExpr encodeCF(Context ctx){
    	BoolExpr enc = ctx.mkTrue();
    	Stack<If> ifStack = new Stack<>();
    	BoolExpr guard = ctx.mkTrue();
    	for(Event e : entry.getSuccessors()) {
    		if(!ifStack.isEmpty()) {
        		If lastIf = ifStack.peek();
        		if(e.equals(lastIf.getMainBranchEvents().get(0))) {
        			guard = ctx.mkAnd(lastIf.cf(), lastIf.getGuard().toZ3Bool(lastIf, ctx));
        		}
        		if(e.equals(lastIf.getElseBranchEvents().get(0))) {
        			guard = ctx.mkAnd(lastIf.cf(), ctx.mkNot(lastIf.getGuard().toZ3Bool(lastIf, ctx)));
        		}
        		if(e.equals(lastIf.getSuccessor())) {
        			guard = ctx.mkOr(lastIf.getExitMainBranch().getCfCond(), lastIf.getExitElseBranch().getCfCond());
        			ifStack.pop();
        		}    			
    		}
    		enc = ctx.mkAnd(enc, e.encodeCF(ctx, guard));
    		guard = e.cf();
    		if(e instanceof CondJump) {
    			guard = ctx.mkAnd(guard, ctx.mkNot(((CondJump)e).getGuard().toZ3Bool(e, ctx)));
    		}
    		if(e instanceof If) {
    			ifStack.add((If)e);
    		}
    	}
        return enc;
    }


    // -----------------------------------------------------------------------------------------------------------------
    // -------------------------------- Preprocessing -----------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    public int eliminateDeadCode(int startId) {
        new DeadCodeElimination(this).apply(startId);
        clearCache();
        return getExit().getOId() + 1;
    }
    
    public void reorderBranches() {
        new BranchReordering(this).apply();
    }

	public void computeDependency(BranchEquivalence b) {
		var current = new HashMap<Register,LinkedList<Event>>();
		for(Event e : entry.getSuccessors()) {
			if(e instanceof RegReaderData || e instanceof MemEvent) {
				ImmutableMap.Builder<Register,ImmutableList<Event>> m = ImmutableMap.builder();
				//NOTE duplicate calls to dependency if address and value share a register
				if(e instanceof RegReaderData)
					for(var r : ((RegReaderData)e).getDataRegs())
						m.put(r,dependency(b,e,current.get(r)));
				if(e instanceof MemEvent)
					for(var r : ((MemEvent)e).getAddress().getRegs())
						m.put(r,dependency(b,e,current.get(r)));
				e.setDependency(m.build());
			}
			if(e instanceof RegWriter) {
				var r = ((RegWriter)e).getResultRegister();
				var c = current.computeIfAbsent(r,k->new LinkedList<>());
				//filter all events that imply being overwritten
				if(e.cfImpliesExec())
					c.removeIf(x->b.isImplied(x,e));
				c.add(e);
			}
		}
	}

	private ImmutableList<Event> dependency(BranchEquivalence b, Event e, LinkedList<Event> c) {
		if(null==c)
			return ImmutableList.of();
		assert!c.isEmpty();
		//get the last event in c that is implied by e
		int size = 0;
		for(var i = c.descendingIterator(); i.hasNext();) {
			++size;
			var ee = i.next();
			if(ee.cfImpliesExec() && b.isImplied(e,ee))
				break;
		}
		//filter all remaining events that are mutually exclusive to e
		var a = new ArrayList<Event>(size);
		for(var i = c.listIterator(c.size()-size); i.hasNext();) {
			var ee = i.next();
			if(!b.areMutuallyExclusive(e,ee))
				a.add(ee);
		}
		return ImmutableList.copyOf(a);
	}
}
