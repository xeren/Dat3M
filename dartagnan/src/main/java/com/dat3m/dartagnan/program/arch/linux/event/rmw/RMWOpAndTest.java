package com.dat3m.dartagnan.program.arch.linux.event.rmw;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.op.COpBin;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Seq;
import com.dat3m.dartagnan.program.event.Local;
import com.dat3m.dartagnan.program.event.rmw.RMWLoad;
import com.dat3m.dartagnan.program.event.rmw.RMWStore;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.wmm.utils.Arch;

public class RMWOpAndTest extends RMWAbstract implements RegWriter, RegReaderData {

    private IOpBin op;

    public RMWOpAndTest(IExpr address, Register register, ExprInterface value, IOpBin op) {
        super(address, register, value, "Mb");
        this.op = op;
    }

    @Override
    public Thread compile(Arch target) {
        if(target == Arch.NONE) {
            Register dummy = new Register(null, resultRegister.getThreadId());
            RMWLoad load = new RMWLoad(dummy, address, "Relaxed");
            Local local1 = new Local(dummy, new IExprBin(dummy, op, value));
            RMWStore store = new RMWStore(load, address, dummy, "Relaxed");
            Local local2 = new Local(resultRegister, new Atom(dummy, COpBin.EQ, new IConst(0)));

            compileBasic(load);
            compileBasic(store);

            Thread result = new Seq(load, new Seq(local1, new Seq(store, local2)));
            return insertFencesOnMb(result);
        }
        return super.compile(target);
    }

    @Override
    public String toString() {
        return nTimesCondLevel() + resultRegister + " := atomic_" + op.toLinuxName() + "_and_test(" + value + ", " + address + ")";
    }

    @Override
    public RMWOpAndTest clone() {
        if(clone == null){
            clone = new RMWOpAndTest(address, resultRegister, value, op);
            afterClone();
        }
        return (RMWOpAndTest)clone;
    }
}