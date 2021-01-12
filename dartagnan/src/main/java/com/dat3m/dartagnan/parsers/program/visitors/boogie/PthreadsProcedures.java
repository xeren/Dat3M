package com.dat3m.dartagnan.parsers.program.visitors.boogie;

import static com.dat3m.dartagnan.expression.op.COpBin.EQ;
import static com.dat3m.dartagnan.program.utils.EType.SC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.dat3m.dartagnan.expression.Atom;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.parsers.BoogieParser.Call_cmdContext;
import com.dat3m.dartagnan.parsers.BoogieParser.ExprContext;
import com.dat3m.dartagnan.parsers.program.utils.ParsingException;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.atomic.event.AtomicLoad;
import com.dat3m.dartagnan.program.atomic.event.AtomicStore;
import com.dat3m.dartagnan.program.event.Assume;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.Store;
import com.dat3m.dartagnan.program.memory.Location;

public class PthreadsProcedures {

	public static List<String> PTHREADPROCEDURES = Arrays.asList(
		"pthread_create",
		"pthread_cond_init",
		"pthread_cond_wait",
		"pthread_cond_signal",
		"pthread_cond_broadcast",
		"pthread_exit",
		"pthread_getspecific",
		"pthread_join",
		"pthread_key_create",
		"pthread_mutex_init",
		"pthread_mutex_destroy",
		"pthread_mutex_lock",
		"pthread_mutex_unlock",
		"pthread_setspecific");

	public static void handlePthreadsFunctions(VisitorBoogie visitor, Call_cmdContext ctx) {
		String name = ctx.call_params().Define() == null ? ctx.call_params().Ident(0).getText() : ctx.call_params().Ident(1).getText();
		if(name.contains("pthread_create")) {
			pthread_create(visitor, ctx);
			return;
		}
		if(name.contains("pthread_cond_init")) {
			// TODO: Implement this
			return;
		}
		if(name.contains("pthread_cond_wait")) {
			// TODO: Implement this
			return;
		}
		if(name.contains("pthread_cond_signal")) {
			// TODO: Implement this
			return;
		}
		if(name.contains("pthread_cond_broadcast")) {
			// TODO: Implement this
			return;
		}
		if(name.contains("pthread_exit")) {
			// TODO: Implement this
			return;
		}
		if(name.contains("pthread_getspecific")) {
			throw new ParsingException(name + " cannot be handled");
		}
		if(name.contains("pthread_join")) {
			pthread_join(visitor, ctx);
			return;
		}
		if(name.contains("pthread_key_create")) {
			throw new ParsingException(name + " cannot be handled");
		}
		if(name.contains("pthread_mutex_init")) {
			mutexInit(visitor, ctx);
			return;
		}
		if(name.contains("pthread_mutex_destroy")) {
			// TODO: Implement this
			return;
		}
		if(name.contains("pthread_mutex_lock")) {
			mutexLock(visitor, ctx);
			return;
		}
		if(name.contains("pthread_mutex_unlock")) {
			mutexUnlock(visitor, ctx);
			return;
		}
		if(name.contains("pthread_setspecific")) {
			throw new ParsingException(name + " cannot be handled");
		}
		throw new UnsupportedOperationException(name + " procedure is not part of PTHREADPROCEDURES");
	}

	private static void pthread_create(VisitorBoogie visitor, Call_cmdContext ctx) {
		visitor.currentThread++;
		visitor.threadCallingValues.put(visitor.currentThread, new ArrayList<>());
		String namePtr = ctx.call_params().exprs().expr().get(0).getText();
		// This names are global so we don't use currentScope.getID(), but per thread.
		Register threadPtr = visitor.thread.register(namePtr, -1);
		String threadName = ctx.call_params().exprs().expr().get(2).getText();
		ExprInterface callingValue = (ExprInterface) ctx.call_params().exprs().expr().get(3).accept(visitor);
		visitor.threadCallingValues.get(visitor.currentThread).add(callingValue);
		visitor.pool.add(threadPtr, threadName);
		Location loc = visitor.programBuilder.getOrCreateLocation(threadPtr + "_active", -1);
		AtomicStore child = new AtomicStore(loc.getAddress(), new IConst(1, -1), SC);
		child.setCLine(visitor.currentLine);
		visitor.thread.add(child);
	}

	private static void pthread_join(VisitorBoogie visitor, Call_cmdContext ctx) {
		String namePtr = ctx.call_params().exprs().expr().get(0).getText();
		// This names are global so we don't use currentScope.getID(), but per thread.
		Register callReg = visitor.thread.register(namePtr, -1);
		if(visitor.pool.getPtrFromReg(callReg) == null) {
			throw new UnsupportedOperationException("pthread_join cannot be handled");
		}
		Location loc = visitor.programBuilder.getOrCreateLocation(visitor.pool.getPtrFromReg(callReg) + "_active", -1);
		Register reg = visitor.thread.register(null, -1);
		visitor.thread.add(new AtomicLoad(reg, loc.getAddress(), SC));
		visitor.thread.add(new Assume(new Atom(reg, EQ, new IConst(0, -1))));
	}

	private static void mutexInit(VisitorBoogie visitor, Call_cmdContext ctx) {
		ExprContext lock = ctx.call_params().exprs().expr(0);
		ExprContext value = ctx.call_params().exprs().expr(1);
		IExpr lockAddress = (IExpr) lock.accept(visitor);
		IExpr val = (IExpr) value.accept(visitor);
		if(lockAddress != null) {
			visitor.thread.add(new Store(lockAddress, val, SC));
		}
	}

	private static void mutexLock(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register register = visitor.thread.register(null, -1);
		IExpr lockAddress = (IExpr) ctx.call_params().exprs().accept(visitor);
		if(null == lockAddress)
			return;
		Load load = visitor.thread.load(register, lockAddress, SC);
		visitor.thread.add(new Assume(new Atom(register, EQ, new IConst(0, -1))));
		visitor.thread.store(load, new IConst(1, -1), SC);
	}

	private static void mutexUnlock(VisitorBoogie visitor, Call_cmdContext ctx) {
		Register register = visitor.thread.register(null, -1);
		IExpr lockAddress = (IExpr) ctx.call_params().exprs().accept(visitor);
		if(null == lockAddress)
			return;
		Load load = visitor.thread.load(register, lockAddress, SC);
		visitor.thread.add(new Assume(new Atom(register, EQ, new IConst(1, -1))));
		visitor.thread.store(load, new IConst(0, -1), SC);
	}
}
