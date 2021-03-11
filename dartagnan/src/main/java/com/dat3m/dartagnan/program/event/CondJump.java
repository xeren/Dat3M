package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.BConst;
import com.dat3m.dartagnan.expression.BExpr;
import com.dat3m.dartagnan.program.ControlBlock;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.Context;

public class CondJump extends Event implements RegReaderData {

    private Label label;
    private Label label4Copy;
    private final BExpr expr;
    private final ImmutableSet<Register> dataRegs;
	private transient CondJump copy;
	private transient ControlBlock controlThen;
	private transient ControlBlock controlElse;

    public CondJump(BExpr expr, Label label){
        if(label == null){
            throw new IllegalArgumentException("CondJump event requires non null label event");
        }
        if(expr == null){
            throw new IllegalArgumentException("CondJump event requires non null expression");
        }
        this.label = label;
        this.label.addListener(this);
        this.expr = expr;
        dataRegs = expr.getRegs();
        addFilters(EType.ANY, EType.JUMP, EType.REG_READER);
    }

    protected CondJump(CondJump other) {
		super(other);
		this.label = other.label4Copy;
		this.expr = other.expr;
		this.dataRegs = other.dataRegs;
		Event notifier = label != null ? label : other.label;
		notifier.addListener(this);
		copy = other.copy;
    }
    
    public Label getLabel(){
        return label;
    }

    @Override
    public ImmutableSet<Register> getDataRegs(){
        return dataRegs;
    }

    @Override
    public String toString(){
    	if(expr instanceof BConst && ((BConst)expr).getValue()) {
            return "goto " + label;
    	}
        return "if(" + expr + "); then goto " + label;
    }

    @Override
    public void notify(Event label) {
    	if(this.label == null) {
        	this.label = (Label)label;
    	} else if (oId > label.getOId()) {
    		this.label4Copy = (Label)label;
    	}
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void unroll(int bound, Event predecessor) {
        if(label.getOId() < oId){
        	if(bound > 1) {
        		predecessor = copyPath(label, successor, predecessor);
        	}
        	Event next = predecessor;
        	if(bound == 1) {
            	next = new BoundEvent();
        		predecessor.setSuccessor(next);        		
        	}
        	if(successor != null) {
        		successor.unroll(bound, next);
        	}
    	    return;
        }
        super.unroll(bound, predecessor);
    }


	@Override
	public CondJump getCopy(){
		return copy = new CondJump(this);
	}

    
    // Compilation
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public int compile(Arch target, int nextId, Event predecessor) {
        cId = nextId++;
        if(successor == null){
            throw new RuntimeException("Malformed CondJump event");
        }
        return successor.compile(target, nextId, this);
    }


    // Encoding
    // -----------------------------------------------------------------------------------------------------------------

	@Override
	public ControlBlock initialise(Context c, ControlBlock b, ControlMessage m){
		ControlBlock bb = super.initialise(c,b,m);
		assert b == bb;
		if(expr instanceof BConst && ((BConst)expr).getValue()){
			controlThen = b;
			controlElse = new ControlBlock(b,c.mkFalse());
		}
		else{
			controlThen = new ControlBlock(b,c.mkBoolConst("then"+cId));
			controlElse = new ControlBlock(b,controlThen,c.mkBoolConst("else"+cId));
		}
		m.send(label.getCId(),controlThen);
		return controlElse;
	}

	@Override
	public void encode(Context c, Constraint o){
		if(null!=controlElse){
			o.add(c.mkEq(controlThen.variable,c.mkAnd(control.variable,expr.toZ3Bool(this,c))));
			o.add(c.mkEq(controlElse.variable,c.mkAnd(control.variable,c.mkNot(controlThen.variable))));
		}
	}

	/**
	Marks a program location where all branches join.
	Control dependency supported by the associated branching event shall not surpass this event.
	*/
	public static final class End extends Event{

		public End(){
		}

		public End(End other){
			super(other);
		}

		@Override
		public End getCopy(){
			return new End(this);
		}

		@Override
		public String label(){
			return "kill dependency";
		}
	}
}
