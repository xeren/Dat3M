package com.dat3m.dartagnan.parsers.program.visitors;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.op.*;
import com.dat3m.dartagnan.parsers.LitmusCBaseVisitor;
import com.dat3m.dartagnan.parsers.LitmusCParser.*;
import com.dat3m.dartagnan.parsers.program.utils.AssertionHelper;
import com.dat3m.dartagnan.parsers.program.utils.ParsingException;
import com.dat3m.dartagnan.parsers.program.utils.ProgramBuilder;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Label;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.utils.EType;
import org.antlr.v4.runtime.misc.Interval;

import java.util.*;

public class VisitorLitmusC extends LitmusCBaseVisitor<ExprInterface> {

	private final ProgramBuilder programBuilder;
	private ProgramBuilder.T thread;
	private Register returnRegister;

	public VisitorLitmusC(ProgramBuilder b) {
		programBuilder = b;
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Entry point

	public Program visit(MainContext c) {
		visitMain(c);
		return programBuilder.build();
	}

	@Override
	public ExprInterface visitMain(MainContext c) {
		visitVariableDeclaratorList(c.variableDeclaratorList());
		visitProgram(c.program());
		if(c.assertionList() != null) {
			int a = c.assertionList().getStart().getStartIndex();
			int b = c.assertionList().getStop().getStopIndex();
			String raw = c.assertionList().getStart().getInputStream().getText(new Interval(a, b));
			programBuilder.setAssert(AssertionHelper.parseAssertionList(programBuilder, raw));
		}
		if(c.assertionFilter() != null) {
			int a = c.assertionFilter().getStart().getStartIndex();
			int b = c.assertionFilter().getStop().getStopIndex();
			String raw = c.assertionFilter().getStart().getInputStream().getText(new Interval(a, b));
			programBuilder.setAssertFilter(AssertionHelper.parseAssertionFilter(programBuilder, raw));
		}
		return null;
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Variable declarator list, e.g., { int 0:a=0; int 1:b=1; int x=2; }

	@Override
	public ExprInterface visitGlobalDeclaratorLocation(GlobalDeclaratorLocationContext c) {
		int value = Location.DEFAULT_INIT_VALUE;
		if(c.initConstantValue() != null) {
			value = Integer.parseInt(c.initConstantValue().constant().getText());
		}
		programBuilder.initLocEqConst(c.varName().getText(), new IConst(value, -1));
		return null;
	}

	@Override
	public ExprInterface visitGlobalDeclaratorRegister(GlobalDeclaratorRegisterContext c) {
		int value = Location.DEFAULT_INIT_VALUE;
		if(c.initConstantValue() != null) {
			value = Integer.parseInt(c.initConstantValue().constant().getText());
		}
		programBuilder.initRegEqConst(c.threadId().id, c.varName().getText(), new IConst(value, -1));
		return null;
	}

	@Override
	public ExprInterface visitGlobalDeclaratorLocationLocation(GlobalDeclaratorLocationLocationContext c) {
		if(c.Ast() == null) {
			programBuilder.initLocEqLocPtr(c.varName(0).getText(), c.varName(1).getText(), -1);
		} else {
			String rightName = c.varName(1).getText();
			Address address = programBuilder.getPointer(rightName);
			if(address != null) {
				programBuilder.initLocEqConst(c.varName(0).getText(), address);
			} else {
				programBuilder.initLocEqLocVal(c.varName(0).getText(), c.varName(1).getText(), -1);
			}
		}
		return null;
	}

	@Override
	public ExprInterface visitGlobalDeclaratorRegisterLocation(GlobalDeclaratorRegisterLocationContext c) {
		if(c.Ast() == null) {
			programBuilder.initRegEqLocPtr(c.threadId().id, c.varName(0).getText(), c.varName(1).getText(), -1);
		} else {
			String rightName = c.varName(1).getText();
			Address address = programBuilder.getPointer(rightName);
			if(address != null) {
				programBuilder.initRegEqConst(c.threadId().id, c.varName(0).getText(), address);
			} else {
				programBuilder.initRegEqLocVal(c.threadId().id, c.varName(0).getText(), c.varName(1).getText(), -1);
			}
		}
		return null;
	}

	@Override
	public ExprInterface visitGlobalDeclaratorArray(GlobalDeclaratorArrayContext c) {
		String name = c.varName().getText();
		Integer size = c.DigitSequence() != null ? Integer.parseInt(c.DigitSequence().getText()) : null;

		if(c.initArray() == null && size != null && size > 0) {
			programBuilder.addDeclarationArray(name, Collections.nCopies(size, new IConst(0, -1)));
			return null;
		}
		if(c.initArray() != null) {
			if(size == null || c.initArray().arrayElement().size() == size) {
				List<IConst> values = new ArrayList<>();
				for(ArrayElementContext elCtx : c.initArray().arrayElement()) {
					if(elCtx.constant() != null) {
						values.add(new IConst(Integer.parseInt(elCtx.constant().getText()), -1));
					} else {
						String varName = elCtx.varName().getText();
						Address address = programBuilder.getPointer(varName);
						if(address != null) {
							values.add(address);
						} else {
							address = programBuilder.getOrCreateLocation(varName, -1).getAddress();
							values.add(elCtx.Ast() == null ? address : programBuilder.getInitValue(address));
						}
					}
				}
				programBuilder.addDeclarationArray(name, values);
				return null;
			}
		}
		throw new ParsingException("Invalid syntax near " + c.getText());
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Threads (the program itself)

	@Override
	public ExprInterface visitThread(ThreadContext c) {
		thread = programBuilder.thread(c.threadId().id);
		visitThreadArguments(c.threadArguments());

		for(ExpressionContext expressionContext : c.expression())
			expressionContext.accept(this);

		thread = null;
		return null;
	}

	@Override
	public ExprInterface visitThreadArguments(ThreadArgumentsContext c) {
		if(c != null) {
			for(VarNameContext varName : c.varName()) {
				String name = varName.getText();
				Address pointer = programBuilder.getPointer(name);
				if(pointer != null) {
					Register register = thread.register(name, -1);
					thread.local(register, pointer);
				} else {
					Location location = programBuilder.getOrCreateLocation(varName.getText(), -1);
					Register register = thread.register(varName.getText(), -1);
					thread.local(register, location.getAddress());
				}
			}
		}
		return null;
	}

	@Override
	public ExprInterface visitWhileExpression(WhileExpressionContext c) {
		ExprInterface expr = c.re().accept(this);
		Label begin = new Label();
		Label end = new Label();
		thread.add(begin);
		thread.jump(end, new BExprUn(BOpUn.NOT, expr));
		for(ExpressionContext expressionContext : c.expression())
			expressionContext.accept(this);
		thread.jump(begin, new BConst(true));
		thread.add(end);
		return null;
	}

	@Override
	public ExprInterface visitIfExpression(IfExpressionContext c) {
		ExprInterface expr = c.re().accept(this);
		Label exitMainBranch = new Label();
		Label exitElseBranch = new Label();
		thread.jump(exitMainBranch, new BExprUn(BOpUn.NOT, expr));
		for(ExpressionContext expressionContext : c.expression())
			expressionContext.accept(this);
		thread.jump(exitElseBranch, new BConst(true));
		thread.add(exitMainBranch);
		if(c.elseExpression() != null)
			c.elseExpression().accept(this);
		thread.add(exitElseBranch);
		return null;
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Return expressions (memory reads, must have register for return value)

	// Returns new value (the value after computation)

	@Override
	public Register visitReReturnAdd(ReReturnAddContext c) {
		return reReturn(IOpBin.PLUS, c.mo, c.address, c.value.accept(this));
	}

	@Override
	public Register visitReReturnSub(ReReturnSubContext c) {
		return reReturn(IOpBin.MINUS, c.mo, c.address, c.value.accept(this));
	}

	@Override
	public Register visitReReturnInc(ReReturnIncContext c) {
		return reReturn(IOpBin.PLUS, c.mo, c.address, new IConst(1, -1));
	}

	@Override
	public Register visitReReturnDec(ReReturnDecContext c) {
		return reReturn(IOpBin.MINUS, c.mo, c.address, new IConst(1, -1));
	}

	private Register reReturn(IOpBin op, String mo, ReContext address, ExprInterface value) {
		Register register = getOrCreateReturnRegister();
		Register dummy = thread.register(null, register.getPrecision());
		if(EType.MB.equals(mo))
			addFence();
		Load load = thread.load(dummy, getAddress(address), loadMO(mo));
		thread.local(register, new IExprBin(dummy, op, value));
		thread.store(load, register, storeMO(mo));
		if(EType.MB.equals(mo))
			addFence();
		return register;
	}

	@Override
	public Register visitReFetchAdd(ReFetchAddContext c) {
		return reFetch(IOpBin.PLUS, c.mo, c.address, c.value.accept(this));
	}

	@Override
	public Register visitReFetchSub(ReFetchSubContext c) {
		return reFetch(IOpBin.MINUS, c.mo, c.address, c.value.accept(this));
	}

	@Override
	public Register visitReFetchInc(ReFetchIncContext c) {
		return reFetch(IOpBin.PLUS, c.mo, c.address, new IConst(1, -1));
	}

	@Override
	public Register visitReFetchDec(ReFetchDecContext c) {
		return reFetch(IOpBin.MINUS, c.mo, c.address, new IConst(1, -1));
	}

	// Returns old value (the value before computation)
	private Register reFetch(IOpBin op, String mo, ReContext address, ExprInterface value) {
		Register register = getOrCreateReturnRegister();
		Register dummy = register != value ? register : thread.register(null, register.getPrecision());
		if(EType.MB.equals(mo))
			addFence();
		Load load = thread.load(dummy, getAddress(address), loadMO(mo));
		thread.store(load, new IExprBin(dummy, op, value), storeMO(mo));
		if(dummy != register)
			thread.local(register, dummy);
		if(EType.MB.equals(mo))
			addFence();
		return register;
	}

	@Override
	public Register visitReTestSub(ReTestSubContext c) {
		return reTest(IOpBin.MINUS, c.address, c.value.accept(this));
	}

	@Override
	public Register visitReTestInc(ReTestIncContext c) {
		return reTest(IOpBin.PLUS, c.address, new IConst(1, -1));
	}

	@Override
	public Register visitReTestDec(ReTestDecContext c) {
		return reTest(IOpBin.MINUS, c.address, new IConst(1, -1));
	}

	private Register reTest(IOpBin op, ReContext address, ExprInterface value) {
		Register register = getOrCreateReturnRegister();
		Register dummy = thread.register(null, register.getPrecision());
		addFence();
		Load load = thread.load(dummy, getAddress(address));
		thread.local(dummy, new IExprBin(dummy, op, value));
		thread.store(load, dummy);
		thread.local(register, new Atom(dummy, COpBin.EQ, new IConst(0, register.getPrecision())));
		addFence();
		return register;
	}

	// Returns non-zero if the addition was executed, zero otherwise
	@Override
	public Register visitReAddUnless(ReAddUnlessContext c) {
		Register register = getOrCreateReturnRegister();
		ExprInterface value = c.value.accept(this);
		ExprInterface cmp = c.cmp.accept(this);
		IExpr address = getAddress(c.address);
		Register dummy = thread.register(null, register.getPrecision());
		addFence();
		Load load = thread.load(dummy, address, EType.RELAXED);
		Label l = new Label();
		thread.jump(l, new Atom(dummy, COpBin.EQ, cmp));
		thread.store(load, new IExprBin(dummy, IOpBin.PLUS, value), EType.RELAXED);
		thread.add(l);
		thread.local(register, new Atom(dummy, COpBin.NEQ, cmp));
		addFence();
		return register;
	}

	@Override
	public Register visitReXchg(ReXchgContext c) {
		Register register = getOrCreateReturnRegister();
		ExprInterface value = c.value.accept(this);
		IExpr address = getAddress(c.address);
		Register dummy = register != value ? register : thread.register(null, register.getPrecision());
		if(EType.MB.equals(c.mo))
			addFence();
		Load load = thread.load(dummy, address, loadMO(c.mo));
		thread.store(load, value, storeMO(c.mo));
		if(dummy != register)
			thread.local(register, dummy);
		if(EType.MB.equals(c.mo))
			addFence();
		return register;
	}

	@Override
	public Register visitReCmpXchg(ReCmpXchgContext c) {
		Register register = getOrCreateReturnRegister();
		ExprInterface cmp = c.cmp.accept(this);
		ExprInterface value = c.value.accept(this);
		IExpr address = getAddress(c.address);
		Register dummy = register != value && register != cmp ? register : thread.register(null, register.getPrecision());
		if(EType.MB.equals(c.mo))
			addFence();
		Load load = thread.load(dummy, address, loadMO(c.mo));
		Label l = new Label();
		thread.jump(l, new Atom(dummy, COpBin.NEQ, cmp));
		thread.store(load, value, storeMO(c.mo));
		if(dummy != register)
			thread.local(register, dummy);
		thread.add(l);
		if(EType.MB.equals(c.mo))
			addFence();
		return register;
	}

	@Override
	public Register visitReLoad(ReLoadContext c) {
		Register register = getOrCreateReturnRegister();
		thread.load(register, getAddress(c.address), c.mo);
		return register;
	}

	@Override
	public Register visitReDereference(ReDereferenceContext c) {
		Register register = getOrCreateReturnRegister();
		thread.load(register, getAddress(c.address));
		return register;
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Return expressions (register for return value is optional)

	@Override
	public BConst visitReFalse(ReFalseContext c) {
		return new BConst(false);
	}

	@Override
	public BConst visitReTrue(ReTrueContext c) {
		return new BConst(true);
	}

	@Override
	public ExprInterface visitReNot(ReNotContext c) {
		return assignToReturnRegister(getReturnRegister(), new BExprUn(BOpUn.NOT, c.re().accept(this)));
	}

	@Override
	public ExprInterface visitReAnd(ReAndContext c) {
		return assignToReturnRegister(getReturnRegister(), new BExprBin(c.lhs.accept(this), BOpBin.AND, c.rhs.accept(this)));
	}

	@Override
	public ExprInterface visitReOr(ReOrContext c) {
		return assignToReturnRegister(getReturnRegister(), new BExprBin(c.lhs.accept(this), BOpBin.OR, c.rhs.accept(this)));
	}

	@Override
	public ExprInterface visitReEqual(ReEqualContext c) {
		return reRel(COpBin.EQ, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReNotEqual(ReNotEqualContext c) {
		return reRel(COpBin.NEQ, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReLessEqual(ReLessEqualContext c) {
		return reRel(COpBin.LTE, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReGreaterEqual(ReGreaterEqualContext c) {
		return reRel(COpBin.GTE, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReLess(ReLessContext c) {
		return reRel(COpBin.LT, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReGreater(ReGreaterContext c) {
		return reRel(COpBin.GT, c.lhs, c.rhs);
	}

	private ExprInterface reRel(COpBin op, ReContext l, ReContext r) {
		return assignToReturnRegister(getReturnRegister(), new Atom(l.accept(this), op, r.accept(this)));
	}

	@Override
	public ExprInterface visitReSum(ReSumContext c) {
		return reBin(IOpBin.PLUS, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReDiff(ReDiffContext c) {
		return reBin(IOpBin.MINUS, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReBitAnd(ReBitAndContext c) {
		return reBin(IOpBin.AND, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReBitOr(ReBitOrContext c) {
		return reBin(IOpBin.OR, c.lhs, c.rhs);
	}

	@Override
	public ExprInterface visitReXor(ReXorContext c) {
		return reBin(IOpBin.XOR, c.lhs, c.rhs);
	}

	private ExprInterface reBin(IOpBin op, ReContext l, ReContext r) {
		return assignToReturnRegister(getReturnRegister(), new IExprBin(l.accept(this), op, r.accept(this)));
	}

	@Override
	public ExprInterface visitReParenthesis(ReParenthesisContext c) {
		return c.re().accept(this);
	}

	@Override
	public ExprInterface visitReCast(ReCastContext c) {
		Register register = getReturnRegister();
		return assignToReturnRegister(register, c.re().accept(this));
	}

	@Override
	public ExprInterface visitReVarName(ReVarNameContext c) {
		Register register = getReturnRegister();
		Register variable = visitVarName(c.varName());
		return assignToReturnRegister(register, variable);
	}

	@Override
	public ExprInterface visitReConst(ReConstContext c) {
		Register register = getReturnRegister();
		IConst result = new IConst(Integer.parseInt(c.getText()), -1);
		return assignToReturnRegister(register, result);
	}


	// ----------------------------------------------------------------------------------------------------------------
	// NonReturn expressions (all other return expressions are reduced to these ones)

	@Override
	public ExprInterface visitNreAdd(NreAddContext c) {
		return nreOp(IOpBin.PLUS, c.address, c.value.accept(this));
	}

	@Override
	public ExprInterface visitNreSub(NreSubContext c) {
		return nreOp(IOpBin.MINUS, c.address, c.value.accept(this));
	}

	@Override
	public ExprInterface visitNreInc(NreIncContext c) {
		return nreOp(IOpBin.PLUS, c.address, new IConst(1, -1));
	}

	@Override
	public ExprInterface visitNreDec(NreDecContext c) {
		return nreOp(IOpBin.MINUS, c.address, new IConst(1, -1));
	}

	private ExprInterface nreOp(IOpBin op, ReContext address, ExprInterface value) {
		Register register = thread.register(null, -1);
		Load load = thread.load(register, getAddress(address));
		thread.store(load, new IExprBin(register, op, value));
		return null;
	}

	@Override
	public ExprInterface visitNreStore(NreStoreContext c) {
		ExprInterface value = c.value.accept(this);
		if(c.mo.equals(EType.MB)) {
			thread.store(getAddress(c.address), value);
			addFence();
			return null;
		}
		thread.store(getAddress(c.address), value, c.mo);
		return null;
	}

	@Override
	public ExprInterface visitNreWriteOnce(NreWriteOnceContext c) {
		ExprInterface value = c.value.accept(this);
		thread.store(getAddress(c.address), value, c.mo);
		return null;
	}

	@Override
	public ExprInterface visitNreAssignment(NreAssignmentContext c) {
		ExprInterface variable = c.varName().accept(this);
		if(c.Ast() == null) {
			if(variable instanceof Register) {
				returnRegister = (Register) variable;
				c.re().accept(this);
				return null;
			}
			throw new ParsingException("Invalid syntax near " + c.getText());
		}

		if(variable instanceof Address || variable instanceof Register) {
			thread.store((IExpr) variable, c.re().accept(this));
			return null;
		}
		throw new ParsingException("Invalid syntax near " + c.getText());
	}

	@Override
	public ExprInterface visitNreRegDeclaration(NreRegDeclarationContext c) {
		Register register = thread.register(c.varName().getText());
		if(register == null) {
			register = thread.register(c.varName().getText(), -1);
			if(c.re() != null) {
				returnRegister = register;
				c.re().accept(this);
			}
			return null;
		}
		throw new ParsingException("Register " + c.varName().getText() + " is already initialised");
	}

	@Override
	public ExprInterface visitNreFence(NreFenceContext c) {
		thread.fence(c.name);
		return null;
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Utils

	@Override
	public Register visitVarName(VarNameContext c) {
		if(null != thread) {
			Register register = thread.register(c.getText());
			if(register != null) {
				return register;
			}
			Location location = programBuilder.getLocation(c.getText());
			if(location != null) {
				register = thread.register(null, -1);
				thread.load(register, location.getAddress());
				return register;
			}
			return thread.register(c.getText(), -1);
		}
		Location location = programBuilder.getOrCreateLocation(c.getText(), -1);
		Register register = thread.register(null, -1);
		thread.load(register, location.getAddress());
		return register;
	}

	private IExpr getAddress(ReContext c) {
		ExprInterface address = c.accept(this);
		if(address instanceof IExpr) {
			return (IExpr) address;
		}
		throw new ParsingException("Invalid syntax near " + c.getText());
	}

	private Register getReturnRegister() {
		Register register = returnRegister;
		returnRegister = null;
		return register;
	}

	private Register getOrCreateReturnRegister() {
		if(null != returnRegister)
			getReturnRegister();
		return thread.register(null, -1);
	}

	private ExprInterface assignToReturnRegister(Register register, ExprInterface value) {
		if(register != null) {
			thread.local(register, value);
		}
		return value;
	}

	private void addFence() {
		thread.fence(EType.MB);
	}

	private static String loadMO(String mo) {
		return EType.ACQUIRE.equals(mo) ? EType.ACQUIRE : EType.RELAXED;
	}

	private static String storeMO(String mo) {
		return EType.RELEASE.equals(mo) ? EType.RELEASE : EType.RELAXED;
	}
}
