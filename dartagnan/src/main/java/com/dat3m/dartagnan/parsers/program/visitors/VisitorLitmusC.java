package com.dat3m.dartagnan.parsers.program.visitors;

import com.dat3m.dartagnan.expression.*;
import com.dat3m.dartagnan.expression.op.BOpUn;
import com.dat3m.dartagnan.expression.op.COpBin;
import com.dat3m.dartagnan.expression.op.IOpBin;
import com.dat3m.dartagnan.parsers.LitmusCBaseVisitor;
import com.dat3m.dartagnan.parsers.LitmusCParser;
import com.dat3m.dartagnan.parsers.LitmusCVisitor;
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

public class VisitorLitmusC
        extends LitmusCBaseVisitor<Object>
        implements LitmusCVisitor<Object> {

    private final ProgramBuilder programBuilder;
    private ProgramBuilder.T thread;
    private Register returnRegister;

    public VisitorLitmusC(ProgramBuilder pb){
        this.programBuilder = pb;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Entry point

    @Override
    public Program visitMain(LitmusCParser.MainContext ctx) {
        visitVariableDeclaratorList(ctx.variableDeclaratorList());
        visitProgram(ctx.program());
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
    // Variable declarator list, e.g., { int 0:a=0; int 1:b=1; int x=2; }

    @Override
    public Object visitGlobalDeclaratorLocation(LitmusCParser.GlobalDeclaratorLocationContext ctx) {
        int value = Location.DEFAULT_INIT_VALUE;
        if (ctx.initConstantValue() != null) {
            value = Integer.parseInt(ctx.initConstantValue().constant().getText());
        }
        programBuilder.initLocEqConst(ctx.varName().getText(), new IConst(value, -1));
        return null;
    }

    @Override
    public Object visitGlobalDeclaratorRegister(LitmusCParser.GlobalDeclaratorRegisterContext ctx) {
        int value = Location.DEFAULT_INIT_VALUE;
        if (ctx.initConstantValue() != null) {
            value = Integer.parseInt(ctx.initConstantValue().constant().getText());
        }
        programBuilder.initRegEqConst(ctx.threadId().id, ctx.varName().getText(), new IConst(value, -1));
        return null;
    }

    @Override
    public Object visitGlobalDeclaratorLocationLocation(LitmusCParser.GlobalDeclaratorLocationLocationContext ctx) {
        if(ctx.Ast() == null){
            programBuilder.initLocEqLocPtr(ctx.varName(0).getText(), ctx.varName(1).getText(), -1);
        } else {
            String rightName = ctx.varName(1).getText();
            Address address = programBuilder.getPointer(rightName);
            if(address != null){
                programBuilder.initLocEqConst(ctx.varName(0).getText(), address);
            } else {
                programBuilder.initLocEqLocVal(ctx.varName(0).getText(), ctx.varName(1).getText(), -1);
            }
        }
        return null;
    }

    @Override
    public Object visitGlobalDeclaratorRegisterLocation(LitmusCParser.GlobalDeclaratorRegisterLocationContext ctx) {
        if(ctx.Ast() == null){
            programBuilder.initRegEqLocPtr(ctx.threadId().id, ctx.varName(0).getText(), ctx.varName(1).getText(), -1);
        } else {
            String rightName = ctx.varName(1).getText();
            Address address = programBuilder.getPointer(rightName);
            if(address != null){
                programBuilder.initRegEqConst(ctx.threadId().id, ctx.varName(0).getText(), address);
            } else {
                programBuilder.initRegEqLocVal(ctx.threadId().id, ctx.varName(0).getText(), ctx.varName(1).getText(), -1);
            }
        }
        return null;
    }

    @Override
    public Object visitGlobalDeclaratorArray(LitmusCParser.GlobalDeclaratorArrayContext ctx) {
        String name = ctx.varName().getText();
        Integer size = ctx.DigitSequence() != null ? Integer.parseInt(ctx.DigitSequence().getText()) : null;

        if(ctx.initArray() == null && size != null && size > 0){
            programBuilder.addDeclarationArray(name, Collections.nCopies(size, new IConst(0, -1)));
            return null;
        }
        if(ctx.initArray() != null){
            if(size == null || ctx.initArray().arrayElement().size() == size){
                List<IConst> values = new ArrayList<>();
                for(LitmusCParser.ArrayElementContext elCtx : ctx.initArray().arrayElement()){
                    if(elCtx.constant() != null){
                        values.add(new IConst(Integer.parseInt(elCtx.constant().getText()), -1));
                    } else {
                        String varName = elCtx.varName().getText();
                        Address address = programBuilder.getPointer(varName);
                        if(address != null){
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
        throw new ParsingException("Invalid syntax near " + ctx.getText());
    }


    // ----------------------------------------------------------------------------------------------------------------
    // Threads (the program itself)

    @Override
    public Object visitThread(LitmusCParser.ThreadContext ctx) {
        thread = programBuilder.thread(ctx.threadId().id);
        visitThreadArguments(ctx.threadArguments());

        for(LitmusCParser.ExpressionContext expressionContext : ctx.expression())
            expressionContext.accept(this);

        thread = null;
        return null;
    }

    @Override
    public Object visitThreadArguments(LitmusCParser.ThreadArgumentsContext ctx){
        if(ctx != null){
            for(LitmusCParser.VarNameContext varName : ctx.varName()){
                String name = varName.getText();
                Address pointer = programBuilder.getPointer(name);
                if(pointer != null){
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
    public Object visitWhileExpression(LitmusCParser.WhileExpressionContext ctx) {
        ExprInterface expr = (ExprInterface) ctx.re().accept(this);
        Label begin = new Label();
        Label end = new Label();
        thread.add(begin);
        thread.jump(end, new BExprUn(BOpUn.NOT, expr));
        for(LitmusCParser.ExpressionContext expressionContext : ctx.expression())
            expressionContext.accept(this);
        thread.jump(begin, new BConst(true));
        thread.add(end);
        return null;
    }

	@Override
	public Object visitIfExpression(LitmusCParser.IfExpressionContext ctx) {
		ExprInterface expr = (ExprInterface) ctx.re().accept(this);
		Label exitMainBranch = new Label();
		Label exitElseBranch = new Label();
		thread.jump(exitMainBranch, new BExprUn(BOpUn.NOT, expr));
		for(LitmusCParser.ExpressionContext expressionContext : ctx.expression())
			expressionContext.accept(this);
		thread.jump(exitElseBranch, new BConst(true));
		thread.add(exitMainBranch);
		if(ctx.elseExpression() != null)
			ctx.elseExpression().accept(this);
		thread.add(exitElseBranch);
		return null;
	}


    // ----------------------------------------------------------------------------------------------------------------
    // Return expressions (memory reads, must have register for return value)

	// Returns new value (the value after computation)
	@Override
	public IExpr visitReAtomicOpReturn(LitmusCParser.ReAtomicOpReturnContext ctx){
		Register register = getReturnRegister(true);
		ExprInterface value = returnExpressionOrDefault(ctx.value, 1);
		IExpr address = getAddress(ctx.address);
		Register dummy = thread.register(null, register.getPrecision());
		if(EType.MB.equals(ctx.mo))
			addFence();
		Load load = thread.load(dummy, address, loadMO(ctx.mo));
		thread.local(register, new IExprBin(dummy, ctx.op, value));
		thread.store(load, register, storeMO(ctx.mo));
		if(EType.MB.equals(ctx.mo))
			addFence();
		return register;
	}

	// Returns old value (the value before computation)
	@Override
	public IExpr visitReAtomicFetchOp(LitmusCParser.ReAtomicFetchOpContext ctx){
		Register register = getReturnRegister(true);
		ExprInterface value = returnExpressionOrDefault(ctx.value, 1);
		IExpr address = getAddress(ctx.address);
		Register dummy = register != value ? register : thread.register(null, register.getPrecision());
		if(EType.MB.equals(ctx.mo))
			addFence();
		Load load = thread.load(dummy, address, loadMO(ctx.mo));
		thread.store(load, new IExprBin(dummy, ctx.op, value), storeMO(ctx.mo));
		if(dummy != register)
			thread.local(register, dummy);
		if(EType.MB.equals(ctx.mo))
			addFence();
		return register;
	}

	@Override
	public IExpr visitReAtomicOpAndTest(LitmusCParser.ReAtomicOpAndTestContext ctx){
		Register register = getReturnRegister(true);
		ExprInterface value = returnExpressionOrDefault(ctx.value, 1);
		IExpr address = getAddress(ctx.address);
		Register dummy = thread.register(null, register.getPrecision());
		addFence();
		Load load = thread.load(dummy, address, EType.RELAXED);
		thread.local(dummy, new IExprBin(dummy, ctx.op, value));
		thread.store(load, dummy, EType.RELAXED);
		thread.local(register, new Atom(dummy, COpBin.EQ, new IConst(0, register.getPrecision())));
		addFence();
		return register;
	}

	// Returns non-zero if the addition was executed, zero otherwise
	@Override
	public IExpr visitReAtomicAddUnless(LitmusCParser.ReAtomicAddUnlessContext ctx){
		Register register = getReturnRegister(true);
		ExprInterface value = (ExprInterface)ctx.value.accept(this);
		ExprInterface cmp = (ExprInterface)ctx.cmp.accept(this);
		IExpr address = getAddress(ctx.address);
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
	public IExpr visitReXchg(LitmusCParser.ReXchgContext ctx){
		Register register = getReturnRegister(true);
		ExprInterface value = (ExprInterface)ctx.value.accept(this);
		IExpr address = getAddress(ctx.address);
		Register dummy = register != value ? register : thread.register(null, register.getPrecision());
		if(EType.MB.equals(ctx.mo))
			addFence();
		Load load = thread.load(dummy, address, loadMO(ctx.mo));
		thread.store(load, value, storeMO(ctx.mo));
		if(dummy != register)
			thread.local(register, dummy);
		if(EType.MB.equals(ctx.mo))
			addFence();
		return register;
	}

	@Override
	public IExpr visitReCmpXchg(LitmusCParser.ReCmpXchgContext ctx){
		Register register = getReturnRegister(true);
		ExprInterface cmp = (ExprInterface)ctx.cmp.accept(this);
		ExprInterface value = (ExprInterface)ctx.value.accept(this);
		IExpr address = getAddress(ctx.address);
		Register dummy = register != value && register != cmp ? register : thread.register(null, register.getPrecision());
		if(EType.MB.equals(ctx.mo))
			addFence();
		Load load = thread.load(dummy, address, loadMO(ctx.mo));
		Label l = new Label();
		thread.jump(l, new Atom(dummy, COpBin.NEQ, cmp));
		thread.store(load, value, storeMO(ctx.mo));
		if(dummy != register)
			thread.local(register, dummy);
		thread.add(l);
		if(EType.MB.equals(ctx.mo))
			addFence();
		return register;
	}

    @Override
    public IExpr visitReLoad(LitmusCParser.ReLoadContext ctx){
        Register register = getReturnRegister(true);
        thread.load(register, getAddress(ctx.address), ctx.mo);
        return register;
    }

    @Override
    public IExpr visitReReadOnce(LitmusCParser.ReReadOnceContext ctx){
        Register register = getReturnRegister(true);
        thread.load(register, getAddress(ctx.address), ctx.mo);
        return register;
    }

    @Override
    public IExpr visitReReadNa(LitmusCParser.ReReadNaContext ctx){
        Register register = getReturnRegister(true);
        thread.load(register, getAddress(ctx.address));
        return register;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Return expressions (register for return value is optional)

    @Override
    public ExprInterface visitReOpCompare(LitmusCParser.ReOpCompareContext ctx){
        Register register = getReturnRegister(false);
        ExprInterface v1 = (ExprInterface)ctx.re(0).accept(this);
        ExprInterface v2 = (ExprInterface)ctx.re(1).accept(this);
        Atom result = new Atom(v1, ctx.opCompare().op, v2);
        return assignToReturnRegister(register, result);
    }

    @Override
    public ExprInterface visitReOpArith(LitmusCParser.ReOpArithContext ctx){
        Register register = getReturnRegister(false);
        ExprInterface v1 = (ExprInterface)ctx.re(0).accept(this);
        ExprInterface v2 = (ExprInterface)ctx.re(1).accept(this);
        IExpr result = new IExprBin(v1, ctx.opArith().op, v2);
        return assignToReturnRegister(register, result);
    }

    @Override
    public ExprInterface visitReOpBool(LitmusCParser.ReOpBoolContext ctx){
        Register register = getReturnRegister(false);
        ExprInterface v1 = (ExprInterface)ctx.re(0).accept(this);
        ExprInterface v2 = (ExprInterface)ctx.re(1).accept(this);
        BExprBin result = new BExprBin(v1, ctx.opBool().op, v2);
        return assignToReturnRegister(register, result);
    }

    @Override
    public ExprInterface visitReOpBoolNot(LitmusCParser.ReOpBoolNotContext ctx){
        Register register = getReturnRegister(false);
        ExprInterface v = (ExprInterface)ctx.re().accept(this);
        BExprUn result = new BExprUn(BOpUn.NOT, v);
        return assignToReturnRegister(register, result);
    }

    @Override
    public ExprInterface visitReBoolConst(LitmusCParser.ReBoolConstContext ctx){
        return new BConst(ctx.boolConst().value);
    }

    @Override
    public ExprInterface visitReParenthesis(LitmusCParser.ReParenthesisContext ctx){
        return (ExprInterface)ctx.re().accept(this);
    }

    @Override
    public ExprInterface visitReCast(LitmusCParser.ReCastContext ctx){
        Register register = getReturnRegister(false);
        ExprInterface result = (ExprInterface)ctx.re().accept(this);
        return assignToReturnRegister(register, result);
    }

    @Override
    public ExprInterface visitReVarName(LitmusCParser.ReVarNameContext ctx){
        Register register = getReturnRegister(false);
        IExpr variable = visitVarName(ctx.varName());
        if(variable instanceof Register){
            Register result = (Register)variable;
            return assignToReturnRegister(register, result);
        }
        throw new ParsingException("Invalid syntax near " + ctx.getText());
    }

    @Override
    public ExprInterface visitReConst(LitmusCParser.ReConstContext ctx){
        Register register = getReturnRegister(false);
        IConst result = new IConst(Integer.parseInt(ctx.getText()), -1);
        return assignToReturnRegister(register, result);
    }


    // ----------------------------------------------------------------------------------------------------------------
    // NonReturn expressions (all other return expressions are reduced to these ones)

	@Override
	public Object visitNreAtomicOp(LitmusCParser.NreAtomicOpContext ctx){
		ExprInterface value = returnExpressionOrDefault(ctx.value, 1);
		Register register = thread.register(null, -1);
		IExpr address = getAddress(ctx.address);
		Load load = thread.load(register, address, EType.RELAXED);
		thread.store(load, new IExprBin(register, ctx.op, value), EType.RELAXED);
		return null;
	}

    @Override
    public Object visitNreStore(LitmusCParser.NreStoreContext ctx){
        ExprInterface value = (ExprInterface)ctx.value.accept(this);
        if(ctx.mo.equals(EType.MB)){
            thread.store(getAddress(ctx.address), value, EType.RELAXED);
            addFence();
            return null;
        }
        thread.store(getAddress(ctx.address), value, ctx.mo);
        return null;
    }

    @Override
    public Object visitNreWriteOnce(LitmusCParser.NreWriteOnceContext ctx){
        ExprInterface value = (ExprInterface)ctx.value.accept(this);
        thread.store(getAddress(ctx.address), value, ctx.mo);
        return null;
    }

    @Override
    public Object visitNreAssignment(LitmusCParser.NreAssignmentContext ctx){
        ExprInterface variable = (ExprInterface)ctx.varName().accept(this);
        if(ctx.Ast() == null){
            if(variable instanceof Register){
                returnRegister = (Register)variable;
                ctx.re().accept(this);
                return null;
            }
            throw new ParsingException("Invalid syntax near " + ctx.getText());
        }

        ExprInterface value = (ExprInterface)ctx.re().accept(this);
        if(variable instanceof Address || variable instanceof Register){
            thread.store((IExpr) variable, value);
            return null;
        }
        throw new ParsingException("Invalid syntax near " + ctx.getText());
    }

    @Override
    public Object visitNreRegDeclaration(LitmusCParser.NreRegDeclarationContext ctx){
        Register register = thread.register(ctx.varName().getText());
        if(register == null){
            register = thread.register(ctx.varName().getText(), -1);
            if(ctx.re() != null){
                returnRegister = register;
                ctx.re().accept(this);
            }
            return null;
        }
        throw new ParsingException("Register " + ctx.varName().getText() + " is already initialised");
    }

    @Override
    public Object visitNreFence(LitmusCParser.NreFenceContext ctx){
        thread.fence(ctx.name);
        return null;
    }


    // ----------------------------------------------------------------------------------------------------------------
    // Utils

    @Override
    public IExpr visitVarName(LitmusCParser.VarNameContext ctx){
        if(null != thread){
            Register register = thread.register(ctx.getText());
            if(register != null){
                return register;
            }
            Location location = programBuilder.getLocation(ctx.getText());
            if(location != null){
                register = thread.register(null, -1);
                thread.load(register, location.getAddress());
                return register;
            }
            return thread.register(ctx.getText(), -1);
        }
        Location location = programBuilder.getOrCreateLocation(ctx.getText(), -1);
        Register register = thread.register(null, -1);
        thread.load(register, location.getAddress());
        return register;
    }

    private IExpr getAddress(LitmusCParser.ReContext ctx){
        ExprInterface address = (ExprInterface)ctx.accept(this);
        if(address instanceof IExpr){
           return (IExpr)address;
        }
        throw new ParsingException("Invalid syntax near " + ctx.getText());
    }

    private ExprInterface returnExpressionOrDefault(LitmusCParser.ReContext ctx, int defaultValue){
        return ctx != null ? (ExprInterface)ctx.accept(this) : new IConst(defaultValue, -1);
    }

    private Register getReturnRegister(boolean createOnNull){
        Register register = returnRegister;
        if(register == null && createOnNull){
            return thread.register(null, -1);
        }
        returnRegister = null;
        return register;
    }

    private ExprInterface assignToReturnRegister(Register register, ExprInterface value){
        if(register != null){
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
