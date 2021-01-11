package com.dat3m.dartagnan.program.arch.pts.event;

import com.dat3m.dartagnan.program.arch.pts.utils.Mo;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.google.common.collect.ImmutableSet;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Fence;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.event.Store;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;

public class Write extends MemEvent implements RegReaderData {

	private final ExprInterface value;
	private final String mo;
	private final ImmutableSet<Register> dataRegs;

	public Write(IExpr address, ExprInterface value, String mo) {
		super(address, mo);
		this.value = value;
		this.mo = mo;
		this.dataRegs = value.getRegs();
		addFilters(EType.ANY, EType.VISIBLE, EType.MEMORY, EType.WRITE, EType.REG_READER);
	}

	private Write(Write other) {
		super(other);
		this.value = other.value;
		this.mo = other.mo;
		this.dataRegs = other.dataRegs;
	}

	@Override
	public ImmutableSet<Register> getDataRegs() {
		return dataRegs;
	}

	@Override
	public String toString() {
		return "store(*" + address + ", " + value + (mo != null ? ", " + mo : "") + ")";
	}


	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Write getCopy() {
		return new Write(this);
	}


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Event[] compile(Arch target) {
		Store store = new Store(address, value, mo);
		switch(target) {
			case NONE:
				break;
			case TSO:
				if(Mo.SC.equals(mo)) {
					return new Event[]{store, new Fence("Mfence")};
				}
				break;
			case POWER:
				if(Mo.RELEASE.equals(mo)) {
					return new Event[]{new Fence("Lwsync"), store};
				}
				else if(Mo.SC.equals(mo)) {
					return new Event[]{new Fence("Sync"), store};
				}
				break;
			case ARM:
			case ARM8:
				if(Mo.RELEASE.equals(mo) || Mo.SC.equals(mo)) {
					Fence fence = new Fence("Ish");
					if(Mo.SC.equals(mo)) {
						return new Event[]{fence, store, new Fence("Ish")};
					}
					return new Event[]{fence, store};
				}
				break;
			default:
				throw new UnsupportedOperationException("Compilation to " + target + " is not supported for " + this);
		}
		return new Event[]{store};
	}
}
