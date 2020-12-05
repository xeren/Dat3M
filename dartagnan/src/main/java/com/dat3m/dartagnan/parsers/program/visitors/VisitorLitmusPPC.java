package com.dat3m.dartagnan.parsers.program.visitors;

import com.dat3m.dartagnan.expression.Atom;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.IExprBin;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.parsers.LitmusPPCBaseVisitor;
import com.dat3m.dartagnan.parsers.LitmusPPCParser;
import com.dat3m.dartagnan.parsers.LitmusPPCVisitor;
import com.dat3m.dartagnan.parsers.program.utils.AssertionHelper;
import com.dat3m.dartagnan.parsers.program.utils.ParsingException;
import com.dat3m.dartagnan.parsers.program.utils.ProgramBuilder;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.*;
import com.google.common.collect.ImmutableSet;
import org.antlr.v4.runtime.misc.Interval;

public class VisitorLitmusPPC
	extends LitmusPPCBaseVisitor<Object>
	implements LitmusPPCVisitor<Object> {

	private final static ImmutableSet<String> fences = ImmutableSet.of("Sync", "Lwsync", "Isync");

	private final ProgramBuilder programBuilder;
	private ProgramBuilder.T[] threadArray;
	private ProgramBuilder.T thread;
	private Cmp[] cmp;
	private Cmp cmpIn;
	private Cmp cmpOut;

	public VisitorLitmusPPC(ProgramBuilder pb){
		this.programBuilder = pb;
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Entry point

	@Override
	public Object visitMain(LitmusPPCParser.MainContext ctx) {
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
	public Object visitVariableDeclaratorLocation(LitmusPPCParser.VariableDeclaratorLocationContext ctx) {
		programBuilder.initLocEqConst(ctx.location().getText(), new IConst(Integer.parseInt(ctx.constant().getText()), -1));
		return null;
	}

	@Override
	public Object visitVariableDeclaratorRegister(LitmusPPCParser.VariableDeclaratorRegisterContext ctx) {
		programBuilder.initRegEqConst(ctx.threadId().id, ctx.register().getText(), new IConst(Integer.parseInt(ctx.constant().getText()), -1));
		return null;
	}

	@Override
	public Object visitVariableDeclaratorRegisterLocation(LitmusPPCParser.VariableDeclaratorRegisterLocationContext ctx) {
		programBuilder.initRegEqLocPtr(ctx.threadId().id, ctx.register().getText(), ctx.location().getText(), -1);
		return null;
	}

	@Override
	public Object visitVariableDeclaratorLocationLocation(LitmusPPCParser.VariableDeclaratorLocationLocationContext ctx) {
		programBuilder.initLocEqLocPtr(ctx.location(0).getText(), ctx.location(1).getText(), -1);
		return null;
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Thread declarator list (on top of instructions), e.g. " P0  |   P1  |   P2  ;"

	@Override
	public Object visitThreadDeclaratorList(LitmusPPCParser.ThreadDeclaratorListContext ctx) {
		threadArray = new ProgramBuilder.T[ctx.threadId().size()];
		cmp = new Cmp[threadArray.length];
		int i = 0;
		for(LitmusPPCParser.ThreadIdContext threadCtx : ctx.threadId()){
			threadArray[i++] = programBuilder.thread(threadCtx.id);
		}
		return null;
	}


	// ----------------------------------------------------------------------------------------------------------------
	// Instruction list (the program itself)

	@Override
	public Object visitInstructionRow(LitmusPPCParser.InstructionRowContext ctx) {
		for(int i = 0; i < threadArray.length; i++){
			thread = threadArray[i];
			cmpIn = cmp[i];
			assert null == cmpOut;
			visitInstruction(ctx.instruction(i));
			cmp[i] = cmpOut;
			cmpOut = null;
		}
		return null;
	}

	@Override
	public Object visitLi(LitmusPPCParser.LiContext ctx) {
		Register register = thread.register(ctx.register().getText(), -1);
		IConst constant = new IConst(Integer.parseInt(ctx.constant().getText()), -1);
		thread.add(new Local(register, constant));
		return null;
	}

	@Override
	public Object visitLwz(LitmusPPCParser.LwzContext ctx) {
		Register r1 = thread.register(ctx.register(0).getText(), -1);
		Register ra = thread.registerOrError(ctx.register(1).getText());
		thread.add(new Load(r1, ra, "_rx"));
		return null;
	}

	@Override
	public Object visitLwzx(LitmusPPCParser.LwzxContext ctx) {
		// TODO: Implementation
		throw new ParsingException("lwzx is not implemented");
	}

	@Override
	public Object visitStw(LitmusPPCParser.StwContext ctx) {
		Register r1 = thread.registerOrError(ctx.register(0).getText());
		Register ra = thread.registerOrError(ctx.register(1).getText());
		thread.add(new Store(ra, r1, "_rx"));
		return null;
	}

	@Override
	public Object visitStwx(LitmusPPCParser.StwxContext ctx) {
		// TODO: Implementation
		throw new ParsingException("stwx is not implemented");
	}

	@Override
	public Object visitMr(LitmusPPCParser.MrContext ctx) {
		Register r1 = thread.register(ctx.register(0).getText(), -1);
		Register r2 = thread.registerOrError(ctx.register(1).getText());
		thread.add(new Local(r1, r2));
		return null;
	}

	@Override
	public Object visitAddi(LitmusPPCParser.AddiContext ctx) {
		Register r1 = thread.register(ctx.register(0).getText(), -1);
		Register r2 = thread.registerOrError(ctx.register(1).getText());
		IConst constant = new IConst(Integer.parseInt(ctx.constant().getText()), -1);
		thread.add(new Local(r1, new IExprBin(r2, IOpBin.PLUS, constant)));
		return null;
	}

	@Override
	public Object visitXor(LitmusPPCParser.XorContext ctx) {
		Register r1 = thread.register(ctx.register(0).getText(), -1);
		Register r2 = thread.registerOrError(ctx.register(1).getText());
		Register r3 = thread.registerOrError(ctx.register(2).getText());
		thread.add(new Local(r1, new IExprBin(r2, IOpBin.XOR, r3)));
		return null;
	}

	@Override
	public Object visitCmpw(LitmusPPCParser.CmpwContext ctx) {
		Register r1 = thread.registerOrError(ctx.register(0).getText());
		Register r2 = thread.registerOrError(ctx.register(1).getText());
		cmpOut = new Cmp(r1, r2);
		return null;
	}

	@Override
	public Object visitBranchCond(LitmusPPCParser.BranchCondContext ctx) {
		Label label = thread.label(ctx.Label().getText());
		if(null == cmpIn){
			throw new ParsingException("Invalid syntax near " + ctx.getText());
		}
		Atom expr = new Atom(cmpIn.getLeft(), ctx.cond().op, cmpIn.getRight());
		thread.add(new CondJump(expr, label));
		return null;
	}

	@Override
	public Object visitLabel(LitmusPPCParser.LabelContext ctx) {
		thread.addLabel(ctx.Label().getText());
		return null;
	}

	@Override
	public Object visitFence(LitmusPPCParser.FenceContext ctx) {
		String name = ctx.getText().toLowerCase();
		name = name.substring(0, 1).toUpperCase() + name.substring(1);
		if(fences.contains(name)){
			thread.add(new Fence(name));
			return null;
		}
		throw new ParsingException("Unrecognised fence " + name);
	}
}
