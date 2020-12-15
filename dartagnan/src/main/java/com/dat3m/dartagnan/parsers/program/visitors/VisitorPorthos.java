package com.dat3m.dartagnan.parsers.program.visitors;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.op.BOpUn;
import com.dat3m.dartagnan.parsers.PorthosBaseVisitor;
import com.dat3m.dartagnan.parsers.PorthosParser;
import com.dat3m.dartagnan.parsers.PorthosVisitor;
import com.dat3m.dartagnan.parsers.program.utils.AssertionHelper;
import com.dat3m.dartagnan.parsers.program.utils.ProgramBuilder;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.arch.pts.event.Read;
import com.dat3m.dartagnan.program.arch.pts.event.Write;
import com.dat3m.dartagnan.program.memory.Location;
import org.antlr.v4.runtime.misc.Interval;

public class VisitorPorthos extends PorthosBaseVisitor<Object> implements PorthosVisitor<Object> {

	private final ProgramBuilder programBuilder;
	private ProgramBuilder.T thread;

	public VisitorPorthos(ProgramBuilder pb){
		this.programBuilder = pb;
	}

	@Override
	public Object visitMain(PorthosParser.MainContext ctx) {
		visitVariableDeclaratorList(ctx.variableDeclaratorList());
		visitProgram(ctx.program());
		if(ctx.assertionList() != null){
			int a = ctx.assertionList().getStart().getStartIndex();
			int b = ctx.assertionList().getStop().getStopIndex();
			String raw = ctx.assertionList().getStart().getInputStream().getText(new Interval(a, b));
			programBuilder.setAssert(AssertionHelper.parseAssertionList(programBuilder, raw));
		}
		return programBuilder.build();
	}

	@Override
	public Object visitVariableDeclaratorList(PorthosParser.VariableDeclaratorListContext ctx) {
		for(PorthosParser.LocationContext locationContext : ctx.location()){
			programBuilder.getOrCreateLocation(locationContext.getText(), -1);
		}
		return null;
	}

	@Override
	public Object visitThread(PorthosParser.ThreadContext ctx) {
		int currentThread = ctx.threadId().id;
		thread = programBuilder.thread(currentThread);
		return ctx.expressionSequence().accept(this);
	}

	@Override
	public Event visitExpressionWhile(PorthosParser.ExpressionWhileContext ctx) {
		ExprInterface expr = (ExprInterface)ctx.boolExpr().accept(this);
		Label begin = new Label(".continue");
		Label end = new Label(".break");
		thread.add(begin);
		thread.add(new CondJump(new BExprUn(BOpUn.NOT, expr), end));
		ctx.expressionSequence().accept(this);
		thread.add(new CondJump(new BConst(true), begin));
		thread.add(end);
		return null;
	}

	@Override
	public Object visitExpressionIf(PorthosParser.ExpressionIfContext ctx) {
		ExprInterface expr = (ExprInterface)ctx.boolExpr().accept(this);
		Label exitMainBranch = thread.label(null);
		Label exitElseBranch = thread.label(null);
		CondJump ifEvent = new CondJump(new BExprUn(BOpUn.NOT, expr), exitMainBranch);
		thread.add(ifEvent);
		ctx.expressionSequence(0).accept(this);
		thread.add(new CondJump(new BConst(true), exitElseBranch));
		thread.add(exitMainBranch);
		if(ctx.expressionSequence(1) != null)
			ctx.expressionSequence(1).accept(this);
		thread.add(exitElseBranch);
		return null;
	}

	@Override
	public Event visitInstructionLocal(PorthosParser.InstructionLocalContext ctx) {
		Register register = thread.register(ctx.register().getText(), -1);
		IExpr expr = (IExpr)ctx.arithExpr().accept(this);
		thread.add(new Local(register, expr));
		return null;
	}

	@Override
	public Event visitInstructionLoad(PorthosParser.InstructionLoadContext ctx) {
		Register register = thread.register(ctx.register().getText(), -1);
		Location location = programBuilder.getOrErrorLocation(ctx.location().getText());
		thread.add(new Load(register, location.getAddress(), null));
		return null;
	}

	@Override
	public Event visitInstructionStore(PorthosParser.InstructionStoreContext ctx) {
		IExpr expr = (IExpr)ctx.arithExpr().accept(this);
		Location location = programBuilder.getOrErrorLocation(ctx.location().getText());
		thread.add(new Store(location.getAddress(), expr, null));
		return null;
	}

	@Override
	public Event visitInstructionRead(PorthosParser.InstructionReadContext ctx) {
		Register register = thread.register(ctx.register().getText(), -1);
		Location location = programBuilder.getOrErrorLocation(ctx.location().getText());
		thread.add(new Read(register, location.getAddress(), ctx.MemoryOrder().getText()));
		return null;
	}

	@Override
	public Event visitInstructionWrite(PorthosParser.InstructionWriteContext ctx) {
		IExpr e = (IExpr)ctx.arithExpr().accept(this);
		Location location = programBuilder.getOrErrorLocation(ctx.location().getText());
		thread.add(new Write(location.getAddress(), e, ctx.MemoryOrder().getText()));
		return null;
	}

	@Override
	public Event visitInstructionFence(PorthosParser.InstructionFenceContext ctx) {
		thread.add(new Fence(ctx.getText()));
		return null;
	}

	@Override
	public IExpr visitArithExprAExpr(PorthosParser.ArithExprAExprContext ctx) {
		IExpr e1 = (IExpr)ctx.arithExpr(0).accept(this);
		IExpr e2 = (IExpr)ctx.arithExpr(1).accept(this);
		return new IExprBin(e1, ctx.opArith().op, e2);
	}

	@Override
	public IExpr visitArithExprChild(PorthosParser.ArithExprChildContext ctx) {
		return (IExpr)ctx.arithExpr().accept(this);
	}

	@Override
	public Register visitArithExprRegister(PorthosParser.ArithExprRegisterContext ctx) {
		return thread.registerOrError(ctx.register().getText());
	}

	@Override
	public IConst visitArithExprConst(PorthosParser.ArithExprConstContext ctx) {
		return new IConst(Integer.parseInt(ctx.getText()), -1);
	}

	@Override
	public BExprBin visitBoolExprBExprBin(PorthosParser.BoolExprBExprBinContext ctx) {
		BExpr e1 = (BExpr)ctx.boolExpr(0).accept(this);
		BExpr e2 = (BExpr)ctx.boolExpr(1).accept(this);
		return new BExprBin(e1, ctx.opBoolBin().op, e2);
	}

	@Override
	public BExprUn visitBoolExprBExprUn(PorthosParser.BoolExprBExprUnContext ctx) {
		BExpr e = (BExpr)ctx.boolExpr().accept(this);
		return new BExprUn(ctx.opBoolUn().op, e);
	}

	@Override
	public Atom visitBoolExprAtom(PorthosParser.BoolExprAtomContext ctx) {
		IExpr e1 = (IExpr)ctx.arithExpr(0).accept(this);
		IExpr e2 = (IExpr)ctx.arithExpr(1).accept(this);
		return new Atom(e1, ctx.opCompare().op, e2);
	}

	@Override
	public BExpr visitBoolExprChild(PorthosParser.BoolExprChildContext ctx) {
		return (BExpr)ctx.boolExpr().accept(this);
	}

	@Override
	public BConst visitBoolExprConst(PorthosParser.BoolExprConstContext ctx) {
		return new BConst(Boolean.parseBoolean(ctx.getText()));
	}
}
