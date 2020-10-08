package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import static com.dat3m.dartagnan.program.utils.EType.REG_READER;
import java.util.Collection;

public class RelIdd extends BasicRegRelation {

	public RelIdd() {
		super(REG_READER);
		term = "idd";
		forceDoEncode = true;
	}

	@Override
	protected Collection<Register> getRegisters(Event regReader) {
		return ((RegReaderData) regReader).getDataRegs();
	}
}
