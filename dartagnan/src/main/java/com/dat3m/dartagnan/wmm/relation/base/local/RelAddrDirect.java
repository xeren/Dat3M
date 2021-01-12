package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.MemEvent;

import java.util.*;

public class RelAddrDirect extends BasicRegRelation<MemEvent> {

	public RelAddrDirect(){
		term = "addrDirect";
		forceDoEncode = true;
	}

	@Override
	protected Class<MemEvent> filter() {
		return MemEvent.class;
	}

	@Override
	protected Collection<Register> getRegisters(MemEvent regReader){
		return regReader.getAddress().getRegs();
	}
}
