package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.processing.ExprSimplifier;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.arch.lisa.RMW;
import com.dat3m.dartagnan.program.event.arch.tso.Xchg;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.core.utils.RegWriter;
import com.dat3m.dartagnan.program.event.lang.catomic.AtomicCmpXchg;
import com.dat3m.dartagnan.program.event.lang.catomic.AtomicLoad;
import com.dat3m.dartagnan.program.event.lang.llvm.LlvmLoad;
import com.dat3m.dartagnan.program.event.visitors.EventVisitor;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;

import java.util.HashMap;
import java.util.Map;

public class ConstantPropagation implements ProgramProcessor {

	// =========================== Configurables ===========================

	// =====================================================================

	private ConstantPropagation() {
	}

	public static ConstantPropagation fromConfig(Configuration config) throws InvalidConfigurationException {
		return newInstance();
	}

	public static ConstantPropagation newInstance() {
		return new ConstantPropagation();
	}

	@Override
	public void run(Program program) {
		Preconditions.checkArgument(program.isUnrolled(), "The program needs to be unrolled before constant propagation.");
		Preconditions.checkArgument(!program.isCompiled(), "Constant propagation needs to be run before compilation.");
		for (Thread thread : program.getThreads()) {
			run(thread);
		}
	}

	private void run(Thread thread) {
		Map<Register, ExprInterface> propagationMap = new HashMap<>();
		Map<Event, Map<Register, ExprInterface>> label2PropagationMap = new HashMap<>();
		for (Event current = thread.getEntry().getSuccessor(); current != null; current = current.getSuccessor()) {
			Map<Register, ExprInterface> incoming = label2PropagationMap.remove(current);
			if (incoming != null) {
				merge(propagationMap, incoming);
			}
			if (!current.is(Tag.NOOPT)) {
				current.accept(new ConstantPropagationVisitor(propagationMap));
			}
			if (current instanceof RegWriter) {
				Register register = ((RegWriter) current).getResultRegister();
				ExprInterface expression = current instanceof Local ? ((Local) current).getExpr() : register;
				if (isConstant(expression)) {
					propagationMap.put(register, expression);
				} else {
					propagationMap.remove(register);
				}
			}
			if (current instanceof CondJump) {
				CondJump jump = (CondJump) current;
				if (!jump.isDead()) {
					label2PropagationMap.merge(jump.getLabel(), new HashMap<>(propagationMap), this::merge);
				}
				if (jump.isGoto()) {
					while (true) {
						current = current.getSuccessor();
						if (current == null) {
							break;
						}
						Map<Register, ExprInterface> m = label2PropagationMap.remove(current);
						if (m != null) {
							propagationMap = m;
							break;
						}
					}
					if (current == null) {
						break;
					}
				}
			}
		}
		thread.updateExit(thread.getEntry());
		thread.clearCache();
	}

	private static boolean isConstant(ExprInterface expression) {
		//FIXME nondeterministic subexpressions are also variable
		return expression.getRegs().isEmpty() && !(expression instanceof INonDet);
	}

	private static ExprInterface simplify(ExprInterface input, Map<Register, ExprInterface> map) {
		ExprSimplifier simplifier = new ExprSimplifier();
		if (input instanceof Register) {
			return map.getOrDefault(input, input);
		}
		if (input instanceof IExprUn) {
			IExprUn un = (IExprUn) input;
			IExpr i = un.getInner();
			IExpr inner = (IExpr) simplify(i, map);
			return inner == i ? input : new IExprUn(un.getOp(), inner).visit(simplifier);
		}
		if (input instanceof IExprBin) {
			IExprBin bin = (IExprBin) input;
			IExpr l = bin.getLHS();
			IExpr r = bin.getRHS();
			IExpr lhs = (IExpr) simplify(l, map);
			IExpr rhs = (IExpr) simplify(r, map);
			return lhs == l && rhs == r ? input : new IExprBin(lhs, bin.getOp(), rhs).visit(simplifier);
		}
		if (input instanceof IfExpr) {
			IfExpr ife = (IfExpr) input;
			ExprInterface g = ife.getGuard();
			IExpr t = ife.getTrueBranch();
			IExpr f = ife.getFalseBranch();
			ExprInterface guard = simplify(g, map);
			IExpr tbranch = (IExpr) simplify(t, map);
			IExpr fbranch = (IExpr) simplify(f, map);
			return tbranch == t && fbranch == f && guard == g ? input : new IfExpr((BExpr) guard, tbranch, fbranch).visit(simplifier);
		}
		if (input instanceof Atom) {
			Atom atom = (Atom) input;
			ExprInterface l = atom.getLHS();
			ExprInterface r = atom.getRHS();
			ExprInterface lhs = simplify(l, map);
			ExprInterface rhs = simplify(r, map);
			return lhs == l && rhs == r ? input : new Atom(lhs, atom.getOp(), rhs).visit(simplifier);
		}
		if (input instanceof BExprUn) {
			BExprUn un = (BExprUn) input;
			ExprInterface i = un.getInner();
			ExprInterface inner = simplify(un.getInner(), map);
			return inner == i ? input : new BExprUn(un.getOp(), inner).visit(simplifier);
		}
		if (input instanceof BExprBin) {
			BExprBin bin = (BExprBin) input;
			ExprInterface l = bin.getLHS();
			ExprInterface r = bin.getRHS();
			ExprInterface lhs = simplify(l, map);
			ExprInterface rhs = simplify(r, map);
			return lhs == l && rhs == r ? input : new BExprBin(lhs, bin.getOp(), rhs).visit(simplifier);
		}
		return input;
	}

	private Map<Register, ExprInterface> merge(Map<Register, ExprInterface> x, Map<Register, ExprInterface> y) {
		Preconditions.checkNotNull(x);
		Preconditions.checkNotNull(y);
		x.keySet().retainAll(y.keySet());
		for (Map.Entry<Register, ExprInterface> entry : y.entrySet()) {
			x.computeIfPresent(entry.getKey(), (k, v) -> v.equals(entry.getValue()) ? v : null);
		}
		return x;
	}

	private static class ConstantPropagationVisitor implements EventVisitor<Event> {

		private final Map<Register, ExprInterface> map;

		protected ConstantPropagationVisitor(Map<Register, ExprInterface> map) {
			this.map = map;
		}

		@Override
		public Event visitEvent(Event e) {
			return e;
		}

		@Override
		public Event visitLoad(Load e) {
			setAddress(e);
			return e;
		}

		@Override
		public Event visitMemEvent(MemEvent e) {
			setAddress(e);
			setMemValue(e);
			return e;
		}

		@Override
		public Event visitLocal(Local e) {
			ExprInterface oldExpr = e.getExpr();
			ExprInterface newExpr = simplify(oldExpr, map);
			verifyNotNull(newExpr, oldExpr);
			if (newExpr != oldExpr && !e.is(Tag.ASSERTION)) {
				e.setExpr(newExpr);
			}
			return e;
		}

		@Override
		public Event visitCondJump(CondJump e) {
			ExprInterface oldGuard = e.getGuard();
			ExprInterface newGuard = simplify(oldGuard, map);
			verifyNotNull(newGuard, oldGuard);
			if (newGuard != oldGuard) {
				e.setGuard((BExpr) newGuard);
			}
			return e;
		}

		@Override
		public Event visitXchg(Xchg e) {
			setAddress(e);
			return e;
		}

		@Override
		public Event visitRMW(RMW e) {
			setAddress(e);
			return e;
		}

		@Override
		public Event visitLlvmLoad(LlvmLoad e) {
    		setAddress(e);
    		return e;
    	};

    	@Override
    	public Event visitAtomicLoad(AtomicLoad e) {
			setAddress(e);
			return e;
		}

		@Override
		public Event visitAtomicCmpXchg(AtomicCmpXchg e) {
			setAddress(e);
			setMemValue(e);
			IExpr oldExpectedAddr = e.getExpectedAddr();
			IExpr newExpectedAddr = (IExpr) simplify(oldExpectedAddr, map);
			verifyNotNull(newExpectedAddr, oldExpectedAddr);
			if (newExpectedAddr != oldExpectedAddr) {
				e.setExpectedAddr(newExpectedAddr);
			}
			return e;
		}

		@Override
		public Event visitForceExecution(ForceExecution e) {
			ExprInterface oldCondition = e.getConditionExpression();
			ExprInterface newCondition = simplify(oldCondition, map);
			verifyNotNull(newCondition, oldCondition);
			if (newCondition != oldCondition) {
				e.setConditionExpression(newCondition);
			}
			return e;
		}

		@Override
		public Event visitSchedulePoint(SchedulePoint e) {
			ExprInterface oldCondition = e.getConditionExpression();
			ExprInterface newCondition = simplify(oldCondition, map);
			verifyNotNull(newCondition, oldCondition);
			if (newCondition != oldCondition) {
				e.setConditionExpression(newCondition);
			}
			IExpr oldRank = e.getRankExpression();
			IExpr newRank = (IExpr) simplify(oldRank, map);
			verifyNotNull(newRank, oldRank);
			if (newRank != oldRank) {
				e.setRankExpression(newRank);
			}
			return e;
		}

		private void setAddress(MemEvent e) {
			IExpr oldAddress = e.getAddress();
			IExpr newAddress = (IExpr) simplify(oldAddress, map);
			verifyNotNull(newAddress, oldAddress);
			if (newAddress != oldAddress) {
				e.setAddress(newAddress);
			}
		}

		private void setMemValue(MemEvent e) {
			ExprInterface oldValue = e.getMemValue();
			ExprInterface newValue = simplify(oldValue, map);
			verifyNotNull(newValue, oldValue);
			if (newValue != oldValue) {
				e.setMemValue(newValue);
			}
		}

		private void verifyNotNull(ExprInterface newValue, ExprInterface oldValue) {
			Verify.verifyNotNull(newValue,
					"Expression %s got no value after constant propagation analysis", oldValue);
		}
	}
}