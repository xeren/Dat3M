package com.dat3m.dartagnan.expression;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;

public class INonDet extends IExpr implements ExprInterface {

	INonDetTypes type;

	public INonDet(INonDetTypes type) {
		this.type = type;
	}

	@Override
	public IConst reduce() {
		throw new UnsupportedOperationException("Reduce not supported for " + this);
	}

	@Override
	public IntExpr toZ3Int(Event e, EncodeContext c) {
		return c.context.mkIntConst(Integer.toString(hashCode()));
	}

	@Override
	public IntExpr getLastValueExpr(EncodeContext c) {
		return c.context.mkIntConst(Integer.toString(hashCode()));
	}

	@Override
	public int getIntValue(Event e, EncodeContext c, Model m) {
		return Integer.parseInt(m.getConstInterp(toZ3Int(e, c)).toString());
	}

	@Override
	public String toString() {
		switch(type) {
			case INT:
				return "nondet_int()";
			case UINT:
				return "nondet_uint()";
			case LONG:
				return "nondet_long()";
			case ULONG:
				return "nondet_ulong()";
			case SHORT:
				return "nondet_short()";
			case USHORT:
				return "nondet_ushort()";
			case CHAR:
				return "nondet_char()";
			case UCHAR:
				return "nondet_uchar()";
		}
		throw new UnsupportedOperationException("toString() not supported for " + this);
	}

	public long getMin() {
		switch(type) {
			case INT:
				return Integer.MIN_VALUE;
			case UINT:
				return 0;
			case LONG:
				return Long.MIN_VALUE;
			case ULONG:
				return 0;
			case SHORT:
				return Short.MIN_VALUE;
			case USHORT:
				return 0;
			case CHAR:
				return Byte.MIN_VALUE;
			case UCHAR:
				return 0;
		}
		throw new UnsupportedOperationException("getMin() not supported for " + this);
	}

	public long getMax() {
		switch(type) {
			case INT:
				return Integer.MAX_VALUE;
			case UINT:
				return 4294967295L;
			case LONG:
				return Long.MAX_VALUE;
			case ULONG:
				return -1L;
			case SHORT:
				return Short.MAX_VALUE;
			case USHORT:
				return 65535;
			case CHAR:
				return Byte.MAX_VALUE;
			case UCHAR:
				return 255;
		}
		throw new UnsupportedOperationException("getMin() not supported for " + this);
	}
}
