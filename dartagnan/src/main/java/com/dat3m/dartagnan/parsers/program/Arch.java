package com.dat3m.dartagnan.parsers.program;

import com.dat3m.dartagnan.expression.Atom;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.parsers.program.utils.ProgramBuilder.T;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Fence;
import com.dat3m.dartagnan.program.event.Label;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.Store;

import static com.dat3m.dartagnan.expression.op.COpBin.EQ;

public enum Arch {
	NONE {
		@Override
		public Fence fence(T t) {
			return null;
		}

		@Override
		public Load load(T t, Register r, IExpr a) {
			return t.load(r, a);
		}

		@Override
		public Store store(T t, IExpr a, ExprInterface v) {
			return t.store(a, v);
		}

		@Override
		public Store store(T t, Load l, ExprInterface v) {
			return t.store(l, v);
		}
	},
	ARM {
		@Override
		public Fence fence(T t) {
			return t.fence("Ish");
		}

		@Override
		public Load load(T t, Register r, IExpr a) {
			Load l = t.load(r, a);
			t.fence("Ish");
			return l;
		}

		@Override
		public Store store(T t, IExpr a, ExprInterface v) {
			t.fence("Ish");
			Store s = t.store(a, v);
			t.fence("Ish");
			return s;
		}

		@Override
		public Store storeRelease(T t, IExpr a, ExprInterface v) {
			t.fence("Ish");
			return t.store(a, v);
		}

		@Override
		public Store store(T t, Load l, ExprInterface v) {
			t.fence("Ish");
			Store s = t.store(l, v);
			t.fence("Ish");
			return s;
		}

		@Override
		public Store storeRelease(T t, Load l, ExprInterface v) {
			t.fence("Ish");
			return t.store(l, v);
		}
	},
	ARM8 {
		@Override
		public Fence fence(T t) {
			return t.fence("Ish");
		}

		@Override
		public Load load(T t, Register r, IExpr a) {
			Load l = t.load(r, a);
			t.fence("Ish");
			return l;
		}

		@Override
		public Store store(T t, IExpr a, ExprInterface v) {
			t.fence("Ish");
			Store s = t.store(a, v);
			t.fence("Ish");
			return s;
		}

		@Override
		public Store storeRelease(T t, IExpr a, ExprInterface v) {
			t.fence("Ish");
			return t.store(a, v);
		}

		@Override
		public Store store(T t, Load l, ExprInterface v) {
			t.fence("Ish");
			Store s = t.store(l, v);
			t.fence("Ish");
			return s;
		}

		@Override
		public Store storeRelease(T t, Load l, ExprInterface v) {
			t.fence("Ish");
			return t.store(l, v);
		}
	},
	POWER {
		@Override
		public Fence fence(T t) {
			return t.fence("Lwsync");
		}

		@Override
		public Load load(T t, Register r, IExpr a) {
			Load l = t.load(r, a);
			Label x = new Label();
			t.jump(x, new Atom(r, EQ, r));
			t.add(x);
			t.fence("Isync");
			return l;
		}

		@Override
		public Store store(T t, IExpr a, ExprInterface v) {
			t.fence("Sync");
			return t.store(a, v);
		}

		@Override
		public Store storeRelease(T t, IExpr a, ExprInterface v) {
			t.fence("Lwsync");
			return t.store(a, v);
		}

		@Override
		public Store store(T t, Load l, ExprInterface v) {
			t.fence("Sync");
			return t.store(l, v);
		}

		@Override
		public Store storeRelease(T t, Load l, ExprInterface v) {
			t.fence("Lwsync");
			return t.store(l, v);
		}
	},
	TSO {
		@Override
		public Fence fence(T t) {
			return t.fence("Mfence");
		}

		@Override
		public Fence fenceAcquireRelease(T t) {
			return null;
		}

		@Override
		public Load load(T t, Register r, IExpr a) {
			return t.load(r, a);
		}

		@Override
		public Store store(T t, IExpr a, ExprInterface v) {
			Store s =  t.store(a, v);
			t.fence("Mfence");
			return s;
		}

		@Override
		public Store storeRelease(T t, IExpr a, ExprInterface v) {
			return t.store(a, v);
		}

		@Override
		public Store store(T t, Load l, ExprInterface v) {
			Store s =  t.store(l, v);
			t.fence("Mfence");
			return s;
		}

		@Override
		public Store storeRelease(T t, Load l, ExprInterface v) {
			return t.store(l, v);
		}
	};

	public static Arch get(String arch) {
		if(arch != null) {
			arch = arch.trim();
			switch(arch) {
				case "none":
					return NONE;
				case "arm":
					return ARM;
				case "arm8":
					return ARM8;
				case "power":
					return POWER;
				case "tso":
					return TSO;
			}
		}
		throw new UnsupportedOperationException("Unrecognized architecture " + arch);
	}

	@Override
	public String toString() {
		switch(this) {
			case NONE:
				return "none";
			case ARM:
				return "arm";
			case ARM8:
				return "arm8";
			case POWER:
				return "power";
			case TSO:
				return "tso";
		}
		return super.toString();
	}

	public abstract Fence fence(T thread);

	public Fence fenceAcquireRelease(T thread) {
		return fence(thread);
	}

	public Fence fenceAcquire(T thread) {
		return fenceAcquireRelease(thread);
	}

	public Fence fenceRelease(T thread) {
		return fenceAcquireRelease(thread);
	}

	public abstract Load load(T thread, Register register, IExpr address);

	public Load loadAcquire(T thread, Register register, IExpr address) {
		return load(thread, register, address);
	}

	public Load loadConsume(T thread, Register register, IExpr address) {
		return loadAcquire(thread, register, address);
	}

	public abstract Store store(T thread, IExpr address, ExprInterface value);

	public Store storeRelease(T thread, IExpr address, ExprInterface value) {
		return store(thread, address, value);
	}

	public abstract Store store(T thread, Load load, ExprInterface value);

	public Store storeRelease(T thread, Load load, ExprInterface value) {
		return store(thread, load, value);
	}
}
