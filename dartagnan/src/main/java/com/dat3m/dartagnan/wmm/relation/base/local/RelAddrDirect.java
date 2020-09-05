package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import static com.dat3m.dartagnan.program.utils.EType.MEMORY;
import java.util.Collection;

public class RelAddrDirect extends BasicRegRelation {

	public RelAddrDirect() {
		super(MEMORY);
		term = "addrDirect";
		forceDoEncode = true;
	}

	@Override
	protected Collection<Register> getRegisters(Event regReader) {
		return ((MemEvent) regReader).getAddress().getRegs();
	}
}
