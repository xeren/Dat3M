package com.dat3m.dartagnan.utils;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;

public enum Result {
	PASS, FAIL, UNKNOWN, BPASS, BFAIL;

	public static Result getResult(Solver s, Program p, Context ctx) {
		Result res;
		System.out.println(s);
		if(s.check() == Status.SATISFIABLE) {
			s.add(p.encodeNoBoundEventExec(ctx));
			res = s.check() == Status.SATISFIABLE ? FAIL : BFAIL;	
		} else {
			BoolExpr enc = ctx.mkFalse();
			for(Event e : p.getCache().getEvents(FilterBasic.get(EType.BOUND))) {
				enc = ctx.mkOr(enc, e.exec());
			}
			s.pop();
			s.add(enc);
			res = s.check() == Status.SATISFIABLE ? BPASS : PASS;	
		}
		if(p.getAss().getInvert()) {
			res = res.invert();
		}
		return res;
	}
	
	public static Result fromString(String name) {
		switch (name) {
		case "PASS":
			return PASS;
		case "BPASS":
			return BPASS;
		case "FAIL":
			return FAIL;
		case "BFAIL":
			return BFAIL;
		default:
			return UNKNOWN;
		}
	}
	
	public Result invert() {
		switch (this) {
		case PASS:
			return FAIL;
		case BPASS:
			return BFAIL;
		case FAIL:
			return PASS;
		case BFAIL:
			return BPASS;
		default:
			return UNKNOWN;
		}
	}
}
