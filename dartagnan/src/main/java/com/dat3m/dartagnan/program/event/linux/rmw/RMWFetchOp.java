package com.dat3m.dartagnan.program.event.linux.rmw;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.expression.IExprBin;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Seq;
import com.dat3m.dartagnan.program.event.rmw.RMWLoad;
import com.dat3m.dartagnan.program.event.rmw.RMWStore;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.Thread;

public class RMWFetchOp extends RMWAbstract implements RegWriter, RegReaderData {

    private IOpBin op;

    public RMWFetchOp(IExpr address, Register register, ExprInterface value, IOpBin op, String atomic) {
        super(address, register, value, atomic);
        this.op = op;
    }

    @Override
    public Thread compile(String target) {
        if(target.equals("sc")) {
            Register dummy = resultRegister == value ? new Register(null) : resultRegister;
            RMWLoad load = new RMWLoad(dummy, address, getLoadMO());
            RMWStore store = new RMWStore(load, address, new IExprBin(dummy, op, value), getStoreMO());

            compileBasic(load);
            compileBasic(store);

            Thread result = new Seq(load, store);
            result = copyFromDummyToResult(result, dummy);
            return insertFencesOnMb(result);
        }
        return super.compile(target);
    }

    @Override
    public String toString() {
        return nTimesCondLevel() + resultRegister + " := atomic_fetch_" + op.toLinuxName() + atomicToText(atomic) + "(" + value + ", " + address + ")";
    }

    @Override
    public RMWFetchOp clone() {
        if(clone == null){
            Register newReg = resultRegister.clone();
            ExprInterface newValue = resultRegister == value ? newReg : value.clone();
            clone = new RMWFetchOp(address.clone(), newReg, newValue, op, atomic);
            afterClone();
        }
        return (RMWFetchOp)clone;
    }
}