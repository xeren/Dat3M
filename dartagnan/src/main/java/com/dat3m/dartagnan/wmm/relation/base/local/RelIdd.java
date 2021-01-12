package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.RegReaderData;

import java.util.*;

public class RelIdd extends BasicRegRelation<RegReaderData> {

	public RelIdd() {
		term = "idd";
		forceDoEncode = true;
	}

	@Override
	protected Class<RegReaderData> filter() {
		return RegReaderData.class;
	}

	@Override
	protected Collection<Register> getRegisters(RegReaderData regReader) {
		return regReader.getDataRegs();
	}
}
