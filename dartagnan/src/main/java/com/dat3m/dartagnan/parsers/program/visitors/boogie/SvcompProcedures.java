package com.dat3m.dartagnan.parsers.program.visitors.boogie;

import static com.dat3m.dartagnan.expression.op.COpBin.EQ;

import java.util.Arrays;
import java.util.List;

import com.dat3m.dartagnan.expression.Atom;
import com.dat3m.dartagnan.expression.BConst;
import com.dat3m.dartagnan.expression.BNonDet;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.INonDet;
import com.dat3m.dartagnan.expression.INonDetTypes;
import com.dat3m.dartagnan.parsers.BoogieParser.Call_cmdContext;
import com.dat3m.dartagnan.parsers.program.utils.ParsingException;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Assume;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.Local;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.utils.EType;

public class SvcompProcedures {

	public static List<String> SVCOMPPROCEDURES = Arrays.asList(
			"__VERIFIER_assume", 
			"__VERIFIER_error", 
			"__VERIFIER_assert",
			"__VERIFIER_atomic_begin",
			"__VERIFIER_atomic_end",
			"__VERIFIER_nondet_bool",
			"__VERIFIER_nondet_int",
			"__VERIFIER_nondet_uint",
			"__VERIFIER_nondet_unsigned_int",
			"__VERIFIER_nondet_short",
			"__VERIFIER_nondet_ushort",
			"__VERIFIER_nondet_unsigned_short",
			"__VERIFIER_nondet_long",
			"__VERIFIER_nondet_ulong",
			"__VERIFIER_nondet_char",
			"__VERIFIER_nondet_uchar");

	public static void handleSvcompFunction(VisitorBoogie visitor, Call_cmdContext ctx) {
		String name = ctx.call_params().Define() == null ? ctx.call_params().Ident(0).getText() : ctx.call_params().Ident(1).getText();
		if(name.contains("__VERIFIER_assume")) {
			__VERIFIER_assume(visitor, ctx);
			return;
		}
		if(name.contains("__VERIFIER_error")) {
			__VERIFIER_error(visitor);
			return;			
		}
		if(name.contains("__VERIFIER_assert")) {
			__VERIFIER_assert(visitor, ctx);
			return;
		}
		if(name.contains("__VERIFIER_atomic")) {
			__VERIFIER_atomic(visitor, name.contains("begin"));
			return;			
		}
		if(name.contains("__VERIFIER_nondet_bool")) {
			__VERIFIER_nondet_bool(visitor, ctx);
			return;
		}
		if(name.contains("__VERIFIER_nondet_int") || name.contains("__VERIFIER_nondet_uint") || name.contains("__VERIFIER_nondet_unsigned_int") || 
		   name.contains("__VERIFIER_nondet_short") || name.contains("__VERIFIER_nondet_ushort") || name.contains("__VERIFIER_nondet_unsigned_short") ||
		   name.contains("__VERIFIER_nondet_long") || name.contains("__VERIFIER_nondet_ulong") || 
		   name.contains("__VERIFIER_nondet_char") || name.contains("__VERIFIER_nondet_uchar")) {
			__VERIFIER_nondet(visitor, ctx, name);
			return;
		}
		throw new UnsupportedOperationException(name + " procedure is not part of SVCOMPPROCEDURES");
	}

	//TODO: seems to be obsolete after SVCOMP 2020
	private static void __VERIFIER_assume(VisitorBoogie visitor, Call_cmdContext ctx) {
       	ExprInterface c = (ExprInterface)ctx.call_params().exprs().accept(visitor);
		if(c != null) {
			Assume child = new Assume(c);
			child.setCLine(visitor.currentLine);
			visitor.thread.add(child);
		}
	}

	//TODO: seems to be obsolete after SVCOMP 2020
	private static void __VERIFIER_error(VisitorBoogie visitor) {
    	Register ass = visitor.thread.register("assert_" + visitor.assertionIndex, -1);
    	visitor.assertionIndex++;
    	Local event = visitor.thread.local(ass, new BConst(false));
		event.addFilters(EType.ASSERTION);
		event.setCLine(visitor.currentLine);
	}
	
	private static void __VERIFIER_assert(VisitorBoogie visitor, Call_cmdContext ctx) {
    	ExprInterface expr = (ExprInterface)ctx.call_params().exprs().accept(visitor);
    	Register ass = visitor.thread.register("assert_" + visitor.assertionIndex, expr.getPrecision());
    	visitor.assertionIndex++;
    	if(expr instanceof IConst && ((IConst)expr).getValue() == 1) {
    		return;
    	}
    	Local event = visitor.thread.local(ass, expr);
		event.addFilters(EType.ASSERTION);
		event.setCLine(visitor.currentLine);
	}
	
	public static void __VERIFIER_atomic(VisitorBoogie visitor, boolean begin) {
		Register register = visitor.thread.register(null, -1);
		Address lockAddress = visitor.programBuilder.getOrCreateLocation("__VERIFIER_atomic", -1).getAddress();
		Load load = visitor.thread.load(register, lockAddress);
		visitor.thread.add(new Assume(new Atom(register, EQ, new IConst(begin ? 0 : 1, -1))));
		visitor.thread.store(load, new IConst(begin ? 1 : 0, -1));
	}

	private static void __VERIFIER_nondet(VisitorBoogie visitor, Call_cmdContext ctx, String name) {
		INonDetTypes type = null;
		if(name.equals("__VERIFIER_nondet_int")) {
			type = INonDetTypes.INT;
		} else if (name.equals("__VERIFIER_nondet_uint") || name.equals("__VERIFIER_nondet_unsigned_int")) {
			type = INonDetTypes.UINT;
		} else if (name.equals("__VERIFIER_nondet_short")) {
			type = INonDetTypes.SHORT;
		} else if (name.equals("__VERIFIER_nondet_ushort") || name.equals("__VERIFIER_nondet_unsigned_short")) {
			type = INonDetTypes.USHORT;
		} else if (name.equals("__VERIFIER_nondet_long")) {
			type = INonDetTypes.LONG;
		} else if (name.equals("__VERIFIER_nondet_ulong")) {
			type = INonDetTypes.ULONG;
		} else if (name.equals("__VERIFIER_nondet_char")) {
			type = INonDetTypes.CHAR;
		} else if (name.equals("__VERIFIER_nondet_uchar")) {
			type = INonDetTypes.UCHAR;
		} else {
			throw new ParsingException(name + " is not supported");
		}
		String registerName = ctx.call_params().Ident(0).getText();
		Register register = visitor.thread.register(visitor.currentScope.getID() + ":" + registerName);
	    if(register != null){
	    	Local child = visitor.thread.local(register, new INonDet(type, register.getPrecision()));
	    	child.setCLine(visitor.currentLine);
	    }
	}

	private static void __VERIFIER_nondet_bool(VisitorBoogie visitor, Call_cmdContext ctx) {
		String registerName = ctx.call_params().Ident(0).getText();
		Register register = visitor.thread.register(visitor.currentScope.getID() + ":" + registerName);
	    if(register != null){
	    	Local child = visitor.thread.local(register, new BNonDet(register.getPrecision()));
	    	child.setCLine(visitor.currentLine);
	    }
	}
}
