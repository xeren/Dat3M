package com.dat3m.dartagnan.parsers.program.visitors.boogie;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.expression.IExprBin;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.parsers.BoogieParser.Call_cmdContext;
import com.dat3m.dartagnan.parsers.BoogieParser.ExprContext;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Fence;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.Store;

import java.util.Arrays;
import java.util.List;

public class AtomicProcedures {

	public static List<String> ATOMICPROCEDURES = Arrays.asList(
			"atomic_init",
			"atomic_store",
			"atomic_load",
			"atomic_fetch",
			"atomic_exchange",
			"atomic_thread_fence");
	
	public static void handleAtomicFunction(VisitorBoogie visitor, Call_cmdContext ctx) {
		String name = ctx.call_params().Define() == null ? ctx.call_params().Ident(0).getText() : ctx.call_params().Ident(1).getText();
		if(name.contains("atomic_init")) {
			atomicInit(visitor, ctx);
			return;
		}
		if(name.contains("atomic_store")) {
			atomicStore(visitor, ctx);
			return;
		}
		if(name.contains("atomic_load")) {
			atomicLoad(visitor, ctx);
			return;
		}			
		if(name.contains("atomic_fetch")) {
			atomicFetchOp(visitor, ctx);
			return;
		}			
		if(name.contains("atomic_exchange")) {
			atomicXchg(visitor, ctx);
			return;
		}			
		if(name.contains("atomic_thread_fence")) {
			atomicThreadFence(visitor, ctx);
			return;
		}	
        throw new UnsupportedOperationException(name + " procedure is not part of ATOMICPROCEDURES");
	}
	
	private static void atomicInit(VisitorBoogie visitor, Call_cmdContext ctx) {
		IExpr add = (IExpr)ctx.call_params().exprs().expr().get(0).accept(visitor);
		ExprInterface value = (ExprInterface)ctx.call_params().exprs().expr().get(1).accept(visitor);
		visitor.thread.store(add, value).setCLine(visitor.currentLine);
	}

	private static void atomicStore(VisitorBoogie visitor, Call_cmdContext ctx) {
		List<ExprContext> arg = ctx.call_params().exprs().expr();
		IExpr add = (IExpr)arg.get(0).accept(visitor);
		ExprInterface value = (ExprInterface)arg.get(1).accept(visitor);
		int mo = arg.size() <= 2 ? 5 : ((IConst)arg.get(2).accept(visitor)).getValue();
		Store store;
		switch(mo) {
			case 0:
			case 1:
			case 2:
			store = visitor.thread.store(add, value);
			break;
			case 3:
			case 4:
			store = visitor.arch.storeRelease(visitor.thread, add, value);
			break;
			case 5:
			store = visitor.arch.store(visitor.thread, add, value);
			break;
			default:
			throw new UnsupportedOperationException("The memory order is not recognized");
		}
		store.setCLine(visitor.currentLine);
	}

	private static void atomicLoad(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register reg = visitor.thread.register(visitor.currentScope.getID() + ":" + ctx.call_params().Ident(0).getText(), -1);
		IExpr add = (IExpr)ctx.call_params().exprs().expr().get(0).accept(visitor);
		int mo = ctx.call_params().exprs().expr().size() <= 1 ? 5 : ((IConst)ctx.call_params().exprs().expr().get(1).accept(visitor)).getValue();
		load(visitor, mo, reg, add);
	}

	private static void atomicFetchOp(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register reg = visitor.thread.register(visitor.currentScope.getID() + ":" + ctx.call_params().Ident(0).getText(), -1);
		List<ExprContext> arg = ctx.call_params().exprs().expr();
		IExpr add = (IExpr)arg.get(0).accept(visitor);
		ExprInterface value = (IExpr)arg.get(1).accept(visitor);
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
		int mo = arg.size() <= 2 ? 5 : ((IConst)arg.get(2).accept(visitor)).getValue();
		store(visitor, mo, load(visitor, mo, reg, add), new IExprBin(reg, op, value));
	}

	private static void atomicXchg(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register reg = visitor.thread.register(visitor.currentScope.getID() + ":" + ctx.call_params().Ident(0).getText(), -1);
		List<ExprContext> arg = ctx.call_params().exprs().expr();
		IExpr add = (IExpr)arg.get(0).accept(visitor);
		ExprInterface value = (ExprInterface)arg.get(1).accept(visitor);
		int mo = arg.size() <= 2 ? 5 : ((IConst)arg.get(2).accept(visitor)).getValue();
		Register dummy = new Register(".dummy", reg.getThreadId(), reg.getPrecision());
		visitor.thread.local(dummy, value);
		store(visitor, mo, load(visitor, mo, reg, add), dummy);
	}

	private static void atomicThreadFence(VisitorBoogie visitor, Call_cmdContext ctx) {
		Fence fence = null;
		switch(((IConst)ctx.call_params().exprs().expr().get(0).accept(visitor)).getValue()) {
			case 0:
			case 1:
			break;
			case 2:
			fence = visitor.arch.fenceAcquire(visitor.thread);
			break;
			case 3:
			fence = visitor.arch.fenceRelease(visitor.thread);
			break;
			case 4:
			fence = visitor.arch.fenceAcquireRelease(visitor.thread);
			break;
			case 5:
			fence = visitor.arch.fence(visitor.thread);
			break;
			default:
			throw new UnsupportedOperationException("The memory order is not recognized");
		}
		if(null != fence)
			fence.setCLine(visitor.currentLine);
	}

	private static Load load(VisitorBoogie visitor, int memoryOrder, Register register, IExpr address) {
		Load load;
		switch(memoryOrder) {
			case 0:
			case 3:
			load = visitor.thread.load(register, address);
			break;
			case 1:
			load = visitor.arch.loadConsume(visitor.thread, register, address);
			break;
			case 2:
			case 4:
			load = visitor.arch.loadAcquire(visitor.thread, register, address);
			break;
			case 5:
			load = visitor.arch.load(visitor.thread, register, address);
			break;
			default:
			throw new UnsupportedOperationException();
		}
		load.setCLine(visitor.currentLine);
		return load;
	}

	private static void store(VisitorBoogie visitor, int memoryOrder, Load load, ExprInterface value) {
		Store store;
		switch(memoryOrder) {
			case 0:
			case 1:
			case 2:
			store = visitor.thread.store(load, value);
			break;
			case 3:
			case 4:
			store = visitor.arch.storeRelease(visitor.thread, load, value);
			break;
			case 5:
			store = visitor.arch.store(visitor.thread, load, value);
			break;
			default:
			throw new UnsupportedOperationException("The memory order is not recognized");
		}
		store.setCLine(visitor.currentLine);
	}
}
