package com.dat3m.dartagnan.program.event.utils;

import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.program.Register;

public interface RegWriter {

    Register getResultRegister();

    default IntExpr getResultRegisterExpr(EncodeContext context){
        throw new UnsupportedOperationException("RegResultExpr is available only for basic events");
    }
}
