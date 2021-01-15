package com.dat3m.dartagnan.program.atomic.event;

import com.dat3m.dartagnan.expression.Atom;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.parsers.program.Arch;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.CondJump;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Fence;
import com.dat3m.dartagnan.program.event.Label;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.utils.EType;

import static com.dat3m.dartagnan.expression.op.COpBin.EQ;

public class AtomicLoad extends Event {

	private final IExpr address;
	private final Register resultRegister;
	private final String mo;

	public AtomicLoad(Register register, IExpr address, String mo) {
		this.address = address;
		this.resultRegister = register;
		this.mo = mo;
	}

	private AtomicLoad(AtomicLoad other) {
		super(other);
		address = other.address;
		resultRegister = other.resultRegister;
		mo = other.mo;
	}

	@Override
	protected String label() {
		return " " + resultRegister + " " + address + " " + mo + " ";
	}


	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public AtomicLoad getCopy() {
		return new AtomicLoad(this);
	}


	// Compilation
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Event[] compile(Arch target) {
		Load load = new Load(resultRegister, address, mo);
		switch(target) {
			case NONE:
			case TSO:
				break;
			case POWER:
				if(EType.SC.equals(mo) || EType.ACQUIRE.equals(mo) || EType.CONSUME.equals(mo)) {
					Label label = new Label("Jump_" + oId);
					CondJump jump = new CondJump(new Atom(resultRegister, EQ, resultRegister), label);
					Fence fence = new Fence("Isync");
					if(EType.SC.equals(mo)) {
						return new Event[]{new Fence("Sync"), load, jump, label, fence};
					}
					return new Event[]{load, jump, label, fence};
				}
				break;
			case ARM:
			case ARM8:
				if(EType.SC.equals(mo) || EType.ACQUIRE.equals(mo) || EType.CONSUME.equals(mo)) {
					return new Event[]{load, new Fence("Ish")};
				}
				break;
			default:
				throw new UnsupportedOperationException("Compilation to " + target + " is not supported for " + this);
		}
		return new Event[]{load};
	}
}
