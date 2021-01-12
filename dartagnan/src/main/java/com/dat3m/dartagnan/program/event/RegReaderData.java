package com.dat3m.dartagnan.program.event;

import com.google.common.collect.ImmutableSet;
import com.dat3m.dartagnan.program.Register;

public interface RegReaderData {

    ImmutableSet<Register> getDataRegs();
}
