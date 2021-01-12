package com.dat3m.dartagnan.program.atomic.event;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Fence;
import com.dat3m.dartagnan.wmm.utils.Arch;

import static com.dat3m.dartagnan.program.utils.EType.ACQUIRE;
import static com.dat3m.dartagnan.program.utils.EType.ACQ_REL;
import static com.dat3m.dartagnan.program.utils.EType.RELEASE;
import static com.dat3m.dartagnan.program.utils.EType.SC;

public class AtomicThreadFence extends Fence {

	private final String mo;

	public AtomicThreadFence(String mo) {
		super("atomic_thread_fence", mo);
		this.mo = mo;
	}

	private AtomicThreadFence(AtomicThreadFence other) {
		super(other);
		this.mo = other.mo;
	}


	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public AtomicThreadFence getCopy() {
		return new AtomicThreadFence(this);
	}


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Event[] compile(Arch target) {
		switch(target) {
			case NONE:
				break;
			case TSO:
				if(SC.equals(mo)) {
					return new Event[]{new Fence("Mfence")};
				}
				break;
			case POWER:
				if(ACQUIRE.equals(mo) || RELEASE.equals(mo) || ACQ_REL.equals(mo) || SC.equals(mo)) {
					return new Event[]{new Fence("Lwsync")};
				}
				break;
			case ARM:
			case ARM8:
				if(ACQUIRE.equals(mo) || RELEASE.equals(mo) || ACQ_REL.equals(mo) || SC.equals(mo)) {
					return new Event[]{new Fence("Ish")};
				}
				break;
			default:
				throw new UnsupportedOperationException("Compilation to " + target + " is not supported for " + this);
		}
		return new Event[]{};
	}
}
