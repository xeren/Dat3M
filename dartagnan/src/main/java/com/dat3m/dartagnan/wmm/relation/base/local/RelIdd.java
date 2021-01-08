package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;

import java.util.*;

public class RelIdd extends BasicRegRelation {

	public RelIdd() {
		term = "idd";
		forceDoEncode = true;
	}

	@Override
	protected FilterAbstract filter() {
		return FilterBasic.get(EType.REG_READER);
	}

	@Override
	protected Collection<Register> getRegisters(Event regReader) {
		return ((RegReaderData) regReader).getDataRegs();
	}
}
