package com.dat3m.dartagnan.program.event.utils;

import com.dat3m.dartagnan.program.Register;
import java.util.Set;

/**
 * Subclass of events that may read from local registers.
 */
public interface RegReaderData extends com.dat3m.dartagnan.Event {

	Set<Register> getDataRegs();
}
