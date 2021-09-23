package com.dat3m.dartagnan.parsers.program.visitors.boogie;

import com.dat3m.dartagnan.expression.Atom;
import com.dat3m.dartagnan.expression.BNonDet;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.expression.IExprBin;
import com.dat3m.dartagnan.expression.op.COpBin;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.parsers.BoogieParser;
import com.dat3m.dartagnan.parsers.BoogieParser.Call_cmdContext;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.wmm.utils.Arch;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.dat3m.dartagnan.program.EventFactory.*;
import static com.dat3m.dartagnan.program.atomic.utils.Mo.ACQUIRE;
import static com.dat3m.dartagnan.program.atomic.utils.Mo.ACQ_REL;
import static com.dat3m.dartagnan.program.atomic.utils.Mo.CONSUME;
import static com.dat3m.dartagnan.program.atomic.utils.Mo.RELEASE;
import static com.dat3m.dartagnan.program.atomic.utils.Mo.SC;
import static com.dat3m.dartagnan.program.atomic.utils.Mo.intToMo;

public class AtomicProcedures {

	public static List<String> ATOMICPROCEDURES = Arrays.asList(
			"atomic_init",
			"atomic_store",
			"atomic_load",
			"atomic_fetch",
			"atomic_exchange",
			"atomic_compare_exchange",
			"atomic_thread_fence",
			"__DAT3M_CAS");
	
	public static void handleAtomicFunction(VisitorBoogie visitor, Call_cmdContext ctx) {
		String name = ctx.call_params().Define() == null ? ctx.call_params().Ident(0).getText() : ctx.call_params().Ident(1).getText();
		// In the way we compile the code, smack generated empty functions for atomic operations with 
		// prefixes like ".i32" thus we need to check "startswith" and we cannot pattern match by name
		if(name.startsWith("atomic_init")) {
			atomicInit(visitor, ctx);
			return;
		}
		if(name.startsWith("atomic_store")) {
			atomicStore(visitor, ctx);
			return;
		}
		if(name.startsWith("atomic_load")) {
			atomicLoad(visitor, ctx);
			return;
		}
		if(name.startsWith("atomic_fetch")) {
			atomicFetchOp(visitor, ctx);
			return;
		}
		if(name.startsWith("atomic_exchange")) {
			atomicXchg(visitor, ctx);
			return;
		}
		if (name.startsWith("atomic_compare_exchange")) {
			atomicCmpXchg(visitor, ctx);
			return;
		}
		if(name.startsWith("atomic_thread_fence")) {
			atomicThreadFence(visitor, ctx);
			return;
		}		
		if(name.startsWith("__DAT3M_CAS")) {
			DAT3M_CAS(visitor, ctx);
			return;
		}
		
		throw new UnsupportedOperationException(name + " procedure is not part of ATOMICPROCEDURES");		
	}
	
	private static void atomicInit(VisitorBoogie visitor, Call_cmdContext ctx) {
		IExpr add = (IExpr)ctx.call_params().exprs().expr().get(0).accept(visitor);
		ExprInterface value = (ExprInterface)ctx.call_params().exprs().expr().get(1).accept(visitor);
		visitor.programBuilder.addChild(visitor.threadCount, newStore(add, value, null, visitor.currentLine));
	}

	private static void atomicStore(VisitorBoogie visitor, Call_cmdContext ctx) {
		IExpr add = (IExpr)ctx.call_params().exprs().expr().get(0).accept(visitor);
		ExprInterface value = (ExprInterface)ctx.call_params().exprs().expr().get(1).accept(visitor);
		String mo = null;
		if(ctx.call_params().exprs().expr().size() > 2) {
			mo = intToMo(((IConst)ctx.call_params().exprs().expr().get(2).accept(visitor)).getIntValue().intValue());
		}
		if(Arch.POWER.equals(visitor.target)) {
			if(SC.equals(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Power.newSyncBarrier());
			else if(RELEASE.equals(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Power.newLwSyncBarrier());
		}
		if(Arch.ARM.equals(visitor.target) && List.of(SC, RELEASE).contains(mo))
			visitor.programBuilder.addChild(visitor.threadCount, Arm.newISHBarrier());
		visitor.programBuilder.addChild(visitor.threadCount, newStore(add, value, mo));
		if(Arch.TSO.equals(visitor.target) && SC.equals(mo))
			visitor.programBuilder.addChild(visitor.threadCount, X86.newMemoryFence());
		if(Arch.ARM.equals(visitor.target) && SC.equals(mo))
			visitor.programBuilder.addChild(visitor.threadCount, Arm.newISHBarrier());
	}

	private static void atomicLoad(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register reg = visitor.programBuilder.getOrCreateRegister(visitor.threadCount, visitor.currentScope.getID() + ":" + ctx.call_params().Ident(0).getText(), -1);
		IExpr add = (IExpr)ctx.call_params().exprs().expr().get(0).accept(visitor);
		String mo = null;
		if(ctx.call_params().exprs().expr().size() > 1) {
			mo = intToMo(((IConst)ctx.call_params().exprs().expr().get(1).accept(visitor)).getIntValue().intValue());
		}
		if(Arch.POWER.equals(visitor.target) && List.of(SC, ACQUIRE, CONSUME).contains(mo))
			visitor.programBuilder.addChild(visitor.threadCount, Power.newSyncBarrier());
		var load = newLoad(reg, add, mo);
		visitor.programBuilder.addChild(visitor.threadCount, load);
		if(Arch.POWER.equals(visitor.target) && List.of(SC, ACQUIRE, CONSUME).contains(mo)) {
			var label = newLabel("DepFake"+load.getOId());
			visitor.programBuilder.addChild(visitor.threadCount, newFakeCtrlDep(reg, label));
			visitor.programBuilder.addChild(visitor.threadCount, label);
			visitor.programBuilder.addChild(visitor.threadCount, Power.newISyncBarrier());
		}
		if(Arch.ARM.equals(visitor.target) && List.of(SC, ACQUIRE, CONSUME).contains(mo))
			visitor.programBuilder.addChild(visitor.threadCount, Arm.newISHBarrier());
	}

	private static void atomicFetchOp(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register reg = visitor.programBuilder.getOrCreateRegister(visitor.threadCount, visitor.currentScope.getID() + ":" + ctx.call_params().Ident(0).getText(), -1);
		IExpr add = (IExpr)ctx.call_params().exprs().expr().get(0).accept(visitor);
		ExprInterface value = (IExpr)ctx.call_params().exprs().expr().get(1).accept(visitor);
		String mo = null;
		IOpBin op;
		if(ctx.getText().contains("_add")) {
			op = IOpBin.PLUS;
		} else if(ctx.getText().contains("_sub")) {
			op = IOpBin.MINUS;
		} else if(ctx.getText().contains("_and")) {
			op = IOpBin.AND;
		} else if(ctx.getText().contains("_or")) {
			op = IOpBin.OR;
		} else if(ctx.getText().contains("_xor")) {
			op = IOpBin.XOR;
		} else {
			throw new RuntimeException("AtomicFetchOp operation cannot be handled");
		}
		if(ctx.call_params().exprs().expr().size() > 2) {
			mo = intToMo(((IConst)ctx.call_params().exprs().expr().get(2).accept(visitor)).getIntValue().intValue());
		}
		if(Arch.POWER.equals(visitor.target)) {
			if(SC.equals(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Power.newSyncBarrier());
			else if(RELEASE.equals(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Power.newLwSyncBarrier());
		}
		var load = newRMWLoad(reg, add, mo);
		visitor.programBuilder.addChild(visitor.threadCount, load);
		if(List.of(Arch.POWER, Arch.ARM8).contains(visitor.target)) {
			var label = newLabel("FakeDep"+load.getOId());
			visitor.programBuilder.addChild(visitor.threadCount, newFakeCtrlDep(reg, label));
			visitor.programBuilder.addChild(visitor.threadCount, label);
		}
		visitor.programBuilder.addChild(visitor.threadCount, newRMWStore(add, new IExprBin(reg, op, value), mo));
		if(Arch.POWER.equals(visitor.target) && ACQUIRE.equals(mo))
			visitor.programBuilder.addChild(visitor.threadCount, Power.newISyncBarrier());
	}

	private static void atomicXchg(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register reg = visitor.programBuilder.getOrCreateRegister(visitor.threadCount, visitor.currentScope.getID() + ":" + ctx.call_params().Ident(0).getText(), -1);
		IExpr add = (IExpr)ctx.call_params().exprs().expr().get(0).accept(visitor);
		ExprInterface value = (ExprInterface)ctx.call_params().exprs().expr().get(1).accept(visitor);
		String mo = null;
		if(ctx.call_params().exprs().expr().size() > 2) {
			mo = intToMo(((IConst)ctx.call_params().exprs().expr().get(2).accept(visitor)).getIntValue().intValue());
		}
		if(Arch.POWER.equals(visitor.target)) {
			if(SC.equals(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Power.newSyncBarrier());
			else if(RELEASE.equals(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Power.newLwSyncBarrier());
		}
		var load = newRMWLoad(reg, add, mo);
		visitor.programBuilder.addChild(visitor.threadCount, load);
		if(List.of(Arch.ARM8, Arch.POWER).contains(visitor.target)) {
			var label = newLabel("FakeDep"+load.getOId());
			visitor.programBuilder.addChild(visitor.threadCount, newFakeCtrlDep(reg, label));
			visitor.programBuilder.addChild(visitor.threadCount, label);
		}
		visitor.programBuilder.addChild(visitor.threadCount, newRMWStore(add, value, mo));
		if(Arch.POWER.equals(visitor.target) && ACQUIRE.equals(mo))
			visitor.programBuilder.addChild(visitor.threadCount, Power.newISyncBarrier());
	}

	private static void DAT3M_CAS(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register reg = visitor.programBuilder.getOrCreateRegister(visitor.threadCount, visitor.currentScope.getID() + ":" + ctx.call_params().Ident(0).getText(), -1);
		List<BoogieParser.ExprContext> params = ctx.call_params().exprs().expr();
		IExpr addr = (IExpr) params.get(0).accept(visitor);
		IExpr expectedVal = (IExpr) params.get(1).accept(visitor);
		ExprInterface desiredVal = (ExprInterface) params.get(2).accept(visitor);
		String mo = null;
		if(params.size() > 3) {
			mo = intToMo(((IConst) params.get(3).accept(visitor)).getIntValue().intValue());
		}
		var registerValue = new Register(null, visitor.threadCount, -1);
		var load = newRMWLoad(registerValue, addr, mo);
		visitor.programBuilder.addChild(visitor.threadCount, load);
		visitor.programBuilder.addChild(visitor.threadCount, newLocal(reg, new Atom(expectedVal, COpBin.NEQ, registerValue)));
		var exit = newLabel("CmpXchg_exit_"+load.getOId());
		visitor.programBuilder.addChild(visitor.threadCount, newJump(new Atom(reg, COpBin.EQ, new IConst(BigInteger.ONE, reg.getPrecision())), exit));
		visitor.programBuilder.addChild(visitor.threadCount, newRMWStore(addr, desiredVal, mo));
		visitor.programBuilder.addChild(visitor.threadCount, exit);
	}

	private static void atomicThreadFence(VisitorBoogie visitor, Call_cmdContext ctx) {
		String mo = intToMo(((IConst)ctx.call_params().exprs().expr().get(0).accept(visitor)).getIntValue().intValue());
		switch(visitor.target) {
		case NONE:
			break;
		case TSO:
			visitor.programBuilder.addChild(visitor.threadCount, X86.newMemoryFence());
			break;
		case POWER:
			if(List.of(SC, ACQUIRE, RELEASE, ACQ_REL).contains(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Power.newLwSyncBarrier());
			break;
		case ARM:
			if(List.of(SC, ACQUIRE, RELEASE, ACQ_REL).contains(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Arm.newISHBarrier());
			break;
		case ARM8:
			if(List.of(SC, RELEASE, ACQ_REL).contains(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Arm8.DMB.newISHBarrier());
			else if(ACQUIRE.equals(mo))
				visitor.programBuilder.addChild(visitor.threadCount, Arm8.DSB.newISHLDBarrier());
		default:
			throw new UnsupportedOperationException("no support for atomic_thread_fence on " + visitor.target);
		}
	}
	
	private static void atomicCmpXchg(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register reg = visitor.programBuilder.getOrCreateRegister(visitor.threadCount, visitor.currentScope.getID() + ":" + ctx.call_params().Ident(0).getText(), -1);
		List<BoogieParser.ExprContext> params = ctx.call_params().exprs().expr();
		IExpr addr = (IExpr) params.get(0).accept(visitor);
		IExpr expectedAddr = (IExpr) params.get(1).accept(visitor); // NOTE: We assume a register here
		ExprInterface desiredVal = (ExprInterface) params.get(2).accept(visitor);
		String mo = null;
		if(params.size() > 3) {
			mo = intToMo(((IConst) params.get(3).accept(visitor)).getIntValue().intValue());
			// NOTE: We forget about the 5th parameter (MO on fail) because it is never used
			// (see issue #123 for an explanation)
		}
		var registerExpected = new Register(null, visitor.threadCount, -1);
		var loadExpected = newLoad(registerExpected, expectedAddr, null);
		visitor.programBuilder.addChild(visitor.threadCount, loadExpected);
		var registerValue = new Register(null, visitor.threadCount, -1);
		var load = newRMWLoad(registerValue, addr, mo);
		visitor.programBuilder.addChild(visitor.threadCount, load);
		var fail = newLabel("CmpXchg_fail_"+load.getOId());
		visitor.programBuilder.addChild(visitor.threadCount, newJump(new BNonDet(-1), fail));
		visitor.programBuilder.addChild(visitor.threadCount, newAssume(new Atom(registerExpected, COpBin.EQ, registerValue)));
		visitor.programBuilder.addChild(visitor.threadCount, newLocal(reg, new IConst(BigInteger.ZERO, reg.getPrecision())));
		visitor.programBuilder.addChild(visitor.threadCount, newRMWStore(addr, desiredVal, mo));
		var exit = newLabel("CmpXchg_exit_"+load.getOId());
		visitor.programBuilder.addChild(visitor.threadCount, newGoto(exit));
		visitor.programBuilder.addChild(visitor.threadCount, fail);
		if(ctx.getText().contains("strong") || visitor.target.isStrong())
			visitor.programBuilder.addChild(visitor.threadCount, newAssume(new Atom(registerExpected, COpBin.NEQ, registerValue)));
		visitor.programBuilder.addChild(visitor.threadCount, newStore(expectedAddr, registerValue, null));
		visitor.programBuilder.addChild(visitor.threadCount, newLocal(reg, new IConst(BigInteger.ONE, reg.getPrecision())));
		visitor.programBuilder.addChild(visitor.threadCount, exit);
	}
}
