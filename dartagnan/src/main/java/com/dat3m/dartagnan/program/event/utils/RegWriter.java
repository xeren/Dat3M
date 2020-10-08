package com.dat3m.dartagnan.program.event.utils;

import com.dat3m.dartagnan.EncodeContext;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.program.Register;

/**
 * Subclass of events that modify a local register.
 */
public interface RegWriter extends com.dat3m.dartagnan.Event {

    Register getResultRegister();

    default IntExpr getResultRegisterExpr(EncodeContext context){
        throw new UnsupportedOperationException("RegResultExpr is available only for basic events");
    }
}
