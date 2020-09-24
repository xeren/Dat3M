package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.BExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;

public class CondJump extends Jump implements RegReaderData {

    private final BExpr expr;
    private final ImmutableSet<Register> dataRegs;

    public CondJump(BExpr expr, Label label){
        super(label);
        if(expr == null){
            throw new IllegalArgumentException("CondJump event requires non null expression");
        }
        this.expr = expr;
        dataRegs = expr.getRegs();
        addFilters(EType.BRANCH, EType.COND_JUMP, EType.REG_READER);
    }

    protected CondJump(CondJump other) {
		super(other);
		this.expr = other.expr;
		this.dataRegs = other.dataRegs;
    }
    
    @Override
    public ImmutableSet<Register> getDataRegs(){
        return dataRegs;
    }

    @Override
    public String toString(){
        return "if(" + expr + "); then goto " + label;
    }


    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public CondJump getCopy(){
    	return new CondJump(this);
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
    public BoolExpr encodeCF(EncodeContext e, BoolExpr cond) {
        if(cfEnc == null){
            cfCond = (cfCond == null) ? cond : e.or(cfCond, cond);
            BoolExpr ifCond = expr.toZ3Bool(this, e);
            label.addCfCond(e, e.and(ifCond, cfVar));
            cfEnc = e.and(e.eq(cfVar, cfCond), encodeExec(e), successor.encodeCF(e, e.and(e.not(ifCond), cfVar)));
        }
        return cfEnc;
    }
}
