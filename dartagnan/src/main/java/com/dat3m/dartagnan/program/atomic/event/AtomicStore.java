package com.dat3m.dartagnan.program.atomic.event;

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

import static com.dat3m.dartagnan.program.atomic.utils.Mo.RELEASE;
import static com.dat3m.dartagnan.program.atomic.utils.Mo.SC;

public class AtomicStore extends MemEvent implements RegReaderData {

	private final ExprInterface value;
	private final ImmutableSet<Register> dataRegs;
	private final String mo;

	public AtomicStore(IExpr address, ExprInterface value, String mo) {
		super(address);
		this.value = value;
		dataRegs = value.getRegs();
		this.mo = mo;
		addFilters(EType.ANY, EType.VISIBLE, EType.MEMORY, EType.WRITE);
	}

	private AtomicStore(AtomicStore other) {
		super(other);
		value = other.value;
		dataRegs = other.dataRegs;
		mo = other.mo;
	}

	@Override
	public ImmutableSet<Register> getDataRegs() {
		return dataRegs;
	}

	@Override
	public String toString() {
		String tag = mo != null ? "_explicit" : "";
		return "atomic_store" + tag + "(*" + address + ", " + value + (mo != null ? ", " + mo : "") + ")";
	}


	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public AtomicStore getCopy() {
		return new AtomicStore(this);
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
				if(SC.equals(mo)) {
					return new Event[]{store, new Fence("Mfence")};
				}
				break;
			case POWER:
				if(RELEASE.equals(mo)) {
					return new Event[]{new Fence("Lwsync"), store};
				}
				else if(SC.equals(mo)) {
					return new Event[]{new Fence("Sync"), store};
				}
				break;
			case ARM:
			case ARM8:
				if(RELEASE.equals(mo) || SC.equals(mo)) {
					Fence fence = new Fence("Ish");
					if(SC.equals(mo)) {
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
