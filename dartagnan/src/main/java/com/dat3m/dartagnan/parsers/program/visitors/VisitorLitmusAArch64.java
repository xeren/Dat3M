package com.dat3m.dartagnan.parsers.program.visitors;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.parsers.LitmusAArch64BaseVisitor;
import com.dat3m.dartagnan.parsers.LitmusAArch64Parser;
import com.dat3m.dartagnan.parsers.LitmusAArch64Visitor;
import com.dat3m.dartagnan.parsers.program.utils.AssertionHelper;
import com.dat3m.dartagnan.parsers.program.utils.ParsingException;
import com.dat3m.dartagnan.parsers.program.utils.ProgramBuilder;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.arch.aarch64.event.RMWLoadExclusive;
import com.dat3m.dartagnan.program.arch.aarch64.event.StoreExclusive;
import com.dat3m.dartagnan.program.event.*;
import org.antlr.v4.runtime.misc.Interval;

public class VisitorLitmusAArch64 extends LitmusAArch64BaseVisitor<Object>
	implements LitmusAArch64Visitor<Object> {

	private final ProgramBuilder programBuilder;
	private ProgramBuilder.T[] threadArray;
	private ProgramBuilder.T thread;

	public VisitorLitmusAArch64(ProgramBuilder pb){
		this.programBuilder = pb;
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Entry point

	@Override
	public Object visitMain(LitmusAArch64Parser.MainContext ctx) {
		visitThreadDeclaratorList(ctx.program().threadDeclaratorList());
		visitVariableDeclaratorList(ctx.variableDeclaratorList());
		visitInstructionList(ctx.program().instructionList());
		if(ctx.assertionList() != null){
			int a = ctx.assertionList().getStart().getStartIndex();
			int b = ctx.assertionList().getStop().getStopIndex();
			String raw = ctx.assertionList().getStart().getInputStream().getText(new Interval(a, b));
			programBuilder.setAssert(AssertionHelper.parseAssertionList(programBuilder, raw));
		}
		if(ctx.assertionFilter() != null){
			int a = ctx.assertionFilter().getStart().getStartIndex();
			int b = ctx.assertionFilter().getStop().getStopIndex();
			String raw = ctx.assertionFilter().getStart().getInputStream().getText(new Interval(a, b));
			programBuilder.setAssertFilter(AssertionHelper.parseAssertionFilter(programBuilder, raw));
		}
		return programBuilder.build();
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Variable declarator list, e.g., { 0:EAX=0; 1:EAX=1; x=2; }

	@Override
	public Object visitVariableDeclaratorLocation(LitmusAArch64Parser.VariableDeclaratorLocationContext ctx) {
		programBuilder.initLocEqConst(ctx.location().getText(), new IConst(Integer.parseInt(ctx.constant().getText()), -1));
		return null;
	}

	@Override
	public Object visitVariableDeclaratorRegister(LitmusAArch64Parser.VariableDeclaratorRegisterContext ctx) {
		programBuilder.initRegEqConst(ctx.threadId().id, ctx.register64().id, new IConst(Integer.parseInt(ctx.constant().getText()), -1));
		return null;
	}

	@Override
	public Object visitVariableDeclaratorRegisterLocation(LitmusAArch64Parser.VariableDeclaratorRegisterLocationContext ctx) {
		programBuilder.initRegEqLocPtr(ctx.threadId().id, ctx.register64().id, ctx.location().getText(), -1);
		return null;
	}

	@Override
	public Object visitVariableDeclaratorLocationLocation(LitmusAArch64Parser.VariableDeclaratorLocationLocationContext ctx) {
		programBuilder.initLocEqLocPtr(ctx.location(0).getText(), ctx.location(1).getText(), -1);
		return null;
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Thread declarator list (on top of instructions), e.g. " P0  |   P1  |   P2  ;"

	@Override
	public Object visitThreadDeclaratorList(LitmusAArch64Parser.ThreadDeclaratorListContext ctx) {
		threadArray = new ProgramBuilder.T[ctx.threadId().size()];
		int i = 0;
		for(LitmusAArch64Parser.ThreadIdContext threadCtx : ctx.threadId()){
			threadArray[i++] = programBuilder.thread(threadCtx.id);
		}
		return null;
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Instruction list (the program itself)

	@Override
	public Object visitInstructionRow(LitmusAArch64Parser.InstructionRowContext ctx) {
		for(int i = 0; i < threadArray.length; i++){
			thread = threadArray[i];
			visitInstruction(ctx.instruction(i));
		}
		return null;
	}

	@Override
	public Object visitMov(LitmusAArch64Parser.MovContext ctx) {
		Register register = thread.register(ctx.rD, -1);
		IExpr expr = ctx.expr32() != null ? (IExpr)ctx.expr32().accept(this) : (IExpr)ctx.expr64().accept(this);
		thread.add(new Local(register, expr));
		return null;
	}

	@Override
	public Object visitCmp(LitmusAArch64Parser.CmpContext ctx) {
		Register register = thread.register(ctx.rD, -1);
		IExpr expr = ctx.expr32() != null ? (IExpr)ctx.expr32().accept(this) : (IExpr)ctx.expr64().accept(this);
		thread.add(new Cmp(register, expr));
		return null;
	}

	@Override
	public Object visitArithmetic(LitmusAArch64Parser.ArithmeticContext ctx) {
		Register rD = thread.register(ctx.rD, -1);
		Register r1 = thread.registerOrError(ctx.rV);
		IExpr expr = ctx.expr32() != null ? (IExpr)ctx.expr32().accept(this) : (IExpr)ctx.expr64().accept(this);
		thread.add(new Local(rD, new IExprBin(r1, ctx.arithmeticInstruction().op, expr)));
		return null;
	}

	@Override
	public Object visitLoad(LitmusAArch64Parser.LoadContext ctx) {
		Register register = thread.register(ctx.rD, -1);
		Register address = thread.registerOrError(ctx.address().id);
		if(ctx.offset() != null){
			address = visitOffset(ctx.offset(), address);
		}
		thread.add(new Load(register, address, ctx.loadInstruction().mo));
		return null;
	}

	@Override
	public Object visitLoadExclusive(LitmusAArch64Parser.LoadExclusiveContext ctx) {
		Register register = thread.register(ctx.rD, -1);
		Register address = thread.registerOrError(ctx.address().id);
		if(ctx.offset() != null){
			address = visitOffset(ctx.offset(), address);
		}
		thread.add(new RMWLoadExclusive(register, address, ctx.loadExclusiveInstruction().mo));
		return null;
	}

	@Override
	public Object visitStore(LitmusAArch64Parser.StoreContext ctx) {
		Register register = thread.register(ctx.rV, -1);
		Register address = thread.registerOrError(ctx.address().id);
		if(ctx.offset() != null){
			address = visitOffset(ctx.offset(), address);
		}
		thread.add(new Store(address, register, ctx.storeInstruction().mo));
		return null;
	}

	@Override
	public Object visitStoreExclusive(LitmusAArch64Parser.StoreExclusiveContext ctx) {
		Register register = thread.register(ctx.rV, -1);
		Register statusReg = thread.register(ctx.rS, -1);
		Register address = thread.registerOrError(ctx.address().id);
		if(ctx.offset() != null){
			address = visitOffset(ctx.offset(), address);
		}
		thread.add(new StoreExclusive(statusReg, address, register, ctx.storeExclusiveInstruction().mo));
		return null;
	}

	@Override
	public Object visitBranch(LitmusAArch64Parser.BranchContext ctx) {
		Label label = thread.label(ctx.label().getText());
		if(ctx.branchCondition() == null){
			thread.add(new CondJump(new BConst(true), label));
			return null;
		}
		Event lastEvent = thread.last();
		if(!(lastEvent instanceof Cmp)){
			throw new ParsingException("Invalid syntax near " + ctx.getText());
		}
		Cmp cmp = (Cmp)lastEvent;
		Atom expr = new Atom(cmp.getLeft(), ctx.branchCondition().op, cmp.getRight());
		thread.add(new CondJump(expr, label));
		return null;
	}

	@Override
	public Object visitBranchRegister(LitmusAArch64Parser.BranchRegisterContext ctx) {
		Register register = thread.registerOrError(ctx.rV);
		Atom expr = new Atom(register, ctx.branchRegInstruction().op, new IConst(0, -1));
		Label label = thread.label(ctx.label().getText());
		thread.add(new CondJump(expr, label));
		return null;
	}

	@Override
	public Object visitBranchLabel(LitmusAArch64Parser.BranchLabelContext ctx) {
		thread.addLabel(ctx.label().getText());
		return null;
	}

	@Override
	public Object visitFence(LitmusAArch64Parser.FenceContext ctx) {
		thread.add(new FenceOpt(ctx.Fence().getText(), ctx.opt));
		return null;
	}

	@Override
	public IExpr visitExpressionRegister64(LitmusAArch64Parser.ExpressionRegister64Context ctx) {
		IExpr expr = thread.register(ctx.register64().id, -1);
		if(ctx.shift() != null){
			IConst val = new IConst(Integer.parseInt(ctx.shift().immediate().constant().getText()), -1);
			expr = new IExprBin(expr, ctx.shift().shiftOperator().op, val);
		}
		return expr;
	}

	@Override
	public IExpr visitExpressionRegister32(LitmusAArch64Parser.ExpressionRegister32Context ctx) {
		IExpr expr = thread.register(ctx.register32().id, -1);
		if(ctx.shift() != null){
			IConst val = new IConst(Integer.parseInt(ctx.shift().immediate().constant().getText()), -1);
			expr = new IExprBin(expr, ctx.shift().shiftOperator().op, val);
		}
		return expr;
	}

	@Override
	public IExpr visitExpressionImmediate(LitmusAArch64Parser.ExpressionImmediateContext ctx) {
		IExpr expr = new IConst(Integer.parseInt(ctx.immediate().constant().getText()), -1);
		if(ctx.shift() != null){
			IConst val = new IConst(Integer.parseInt(ctx.shift().immediate().constant().getText()), -1);
			expr = new IExprBin(expr, ctx.shift().shiftOperator().op, val);
		}
		return expr;
	}

	@Override
	public IExpr visitExpressionConversion(LitmusAArch64Parser.ExpressionConversionContext ctx) {
		// TODO: Implement when adding support for mixed-size accesses
		thread.register(ctx.register32().id, -1);
		return null;
	}

	private Register visitOffset(LitmusAArch64Parser.OffsetContext ctx, Register register){
		Register result = thread.register(null, -1);
		IExpr expr = ctx.immediate() == null
			? thread.registerOrError(ctx.expressionConversion().register32().id)
			: new IConst(Integer.parseInt(ctx.immediate().constant().getText()), -1);
		thread.add(new Local(result, new IExprBin(register, IOpBin.PLUS, expr)));
		return result;
	}
}
