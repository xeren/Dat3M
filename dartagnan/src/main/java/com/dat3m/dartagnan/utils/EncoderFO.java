package com.dat3m.dartagnan.utils;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.*;

import java.util.HashMap;

public class EncoderFO extends Encoder {

	private final BoolSort sortBoolean;
	private final IntSort sortInteger;
	private final FiniteDomainSort sortEvent;
	private final Sort[] sortEventPair;
	private final UnaryPredicate cf;
	private final UnaryPredicate exec;
	private final HashMap<Register,UnaryFunction> rValue = new HashMap<>();
	private final HashMap<Register,UnaryFunction> rResult = new HashMap<>();
	private final UnaryPredicate finalWriter;
	private final HashMap<String,BinaryPredicate> edge = new HashMap<>();
	private final HashMap<String,UnaryFunction> intVar = new HashMap<>();
	private final HashMap<String,BinaryFunction> intCount = new HashMap<>();
	private final HashMap<String,UnaryPredicate> cycleVar = new HashMap<>();
	private final HashMap<String,BinaryPredicate> cycleEdge = new HashMap<>();
	private final BinaryPredicate exclusivePair;
	private final HashMap<Integer,Expr> bound = new HashMap<>();

	public EncoderFO(Context context, int eventCount) {
		super(context);
		sortBoolean = context.mkBoolSort();
		sortInteger = context.mkIntSort();
		sortEvent = context.mkFiniteDomainSort("Event", eventCount);
		sortEventPair = new Sort[]{sortEvent,sortEvent};
		FuncDecl cf = context.mkFuncDecl("cf", sortEvent, sortBoolean);
		this.cf = x -> (BoolExpr)cf.apply(x);
		FuncDecl exec = context.mkFuncDecl("exec", sortEvent, sortBoolean);
		this.exec = x -> (BoolExpr)exec.apply(x);
		FuncDecl finalWriter = context.mkFuncDecl("final", sortEvent, sortBoolean);
		this.finalWriter = x -> (BoolExpr)finalWriter.apply(x);
		FuncDecl exclusivePair = context.mkFuncDecl("exclusive", sortEventPair, sortBoolean);
		this.exclusivePair = (x, y) -> (BoolExpr)exclusivePair.apply(x, y);
	}

	public BinaryPredicate edge(String name) {
		return edge.computeIfAbsent(name, k->{
			FuncDecl f = context.mkFuncDecl("edge." + k, sortEventPair, sortBoolean);
			return (x, y) -> (BoolExpr)f.apply(x, y);
		});
	}

	public BinaryFunction intCount(String relName) {
		return intCount.computeIfAbsent(relName, k->{
			FuncDecl f = context.mkFuncDecl("count." + k, sortEventPair, sortInteger);
			return (x, y) -> (IntExpr)f.apply(x, y);
		});
	}

	public UnaryFunction intVar(String name) {
		return intVar.computeIfAbsent(name, k->{
			FuncDecl f = context.mkFuncDecl("var." + k, sortEvent, sortInteger);
			return x -> (IntExpr)f.apply(x);
		});
	}

	public UnaryPredicate cycleVar(String name) {
		return cycleVar.computeIfAbsent(name, k->{
			FuncDecl f = context.mkFuncDecl("cycle.var." + k, sortEvent, sortBoolean);
			return x -> (BoolExpr)f.apply(x);
		});
	}

	public BinaryPredicate cycleEdge(String name) {
		return cycleEdge.computeIfAbsent(name, k->{
			FuncDecl f = context.mkFuncDecl("cycle.edge." + k, sortEventPair, sortBoolean);
			return (x, y) -> (BoolExpr)f.apply(x, y);
		});
	}

	public Expr bind(int index) {
		return bound.computeIfAbsent(index, k->context.mkConst("x" + k, sortEvent));
	}

	public Pattern pattern(Expr... expression) {
		return context.mkPattern(expression);
	}

	public BoolExpr forall(Expr[] bind, BoolExpr body, Pattern... pattern) {
		return context.mkForall(bind, body, 0, pattern, null, null, null);
	}

	public BoolExpr exists(Expr[] bind, BoolExpr body, Pattern... pattern) {
		return context.mkExists(bind, body, 0, pattern, null, null, null);
	}

	public BoolExpr mkEq(Expr first, Expr second) {
		return context.mkEq(first, second);
	}

	@Override
	public BoolExpr cf(Event event) {
		return cf.of(expr(event));
	}

	@Override
	public BoolExpr exec(Event event) {
		return exec.of(expr(event));
	}

	@Override
	public IntExpr value(Register register, Event event) {
		return rValue.computeIfAbsent(register, k->{
			FuncDecl f = context.mkFuncDecl("register." + k.getName(), sortEvent, sortInteger);
			return x -> (IntExpr)f.apply(x);
		}).of(expr(event));
	}

	@Override
	public IntExpr result(Register register, Event event) {
		return rResult.computeIfAbsent(register, k->{
			FuncDecl f = context.mkFuncDecl("result." + k.getName(), sortEvent, sortInteger);
			return x -> (IntExpr)f.apply(x);
		}).of(expr(event));
	}

	@Override
	public BoolExpr finalWriter(Event event) {
		return finalWriter.of(expr(event));
	}

	@Override
	public BoolExpr mkSeqVar(Event read, int i) {
		return (BoolExpr)context.mkFuncDecl("rf." + i, sortEvent, sortBoolean).apply(expr(read));
	}

	@Override
	public BoolExpr edge(String name, Event e1, Event e2) {
		return edge(name).of(expr(e1), expr(e2));
	}

	@Override
	public IntExpr intVar(String name, Event e) {
		return intVar(name).of(expr(e));
	}

	@Override
	public IntExpr intCount(String name, Event e1, Event e2) {
		return intCount(name).of(expr(e1), expr(e2));
	}

	@Override
	public BoolExpr cycleVar(String name, Event e) {
		return cycleVar(name).of(expr(e));
	}

	@Override
	public BoolExpr cycleEdge(String name, Event e1, Event e2) {
		return cycleEdge(name).of(expr(e1), expr(e2));
	}

	@Override
	public BoolExpr exclusivePair(Event load, Event store) {
		return exclusivePair.of(expr(load), expr(store));
	}

	public interface UnaryPredicate {
		BoolExpr of(Expr operand);
	}

	public interface BinaryPredicate {
		BoolExpr of(Expr first, Expr second);
	}

	public interface UnaryFunction {
		IntExpr of(Expr operand);
	}

	public interface BinaryFunction {
		IntExpr of(Expr first, Expr second);
	}

	private Expr expr(Event event) {
		return context.mkNumeral(event.getCId(), sortEvent);
	}
}
