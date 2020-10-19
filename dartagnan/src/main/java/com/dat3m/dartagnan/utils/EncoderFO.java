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
	public BoolExpr edge(String relName, Event e1, Event e2) {
		return edge.computeIfAbsent(relName, k->{
			FuncDecl f = context.mkFuncDecl("edge." + k, sortEventPair, sortBoolean);
			return (x, y) -> (BoolExpr)f.apply(x, y);
		}).of(expr(e1), expr(e2));
	}

	@Override
	public IntExpr intVar(String relName, Event e) {
		return intVar.computeIfAbsent(relName, k->{
			FuncDecl f = context.mkFuncDecl("var." + k, sortEvent, sortInteger);
			return x -> (IntExpr)f.apply(x);
		}).of(expr(e));
	}

	@Override
	public IntExpr intCount(String relName, Event e1, Event e2) {
		return intCount.computeIfAbsent(relName, k->{
			FuncDecl f = context.mkFuncDecl("count." + k, sortEventPair, sortInteger);
			return (x, y) -> (IntExpr)f.apply(x, y);
		}).of(expr(e1), expr(e2));
	}

	@Override
	public BoolExpr cycleVar(String relName, Event e) {
		return cycleVar.computeIfAbsent(relName, k->{
			FuncDecl f = context.mkFuncDecl("cycle.var." + k, sortEvent, sortBoolean);
			return x -> (BoolExpr)f.apply(x);
		}).of(expr(e));
	}

	@Override
	public BoolExpr cycleEdge(String relName, Event e1, Event e2) {
		return cycleEdge.computeIfAbsent(relName, k->{
			FuncDecl f = context.mkFuncDecl("cycle.edge." + k, sortEventPair, sortBoolean);
			return (x, y) -> (BoolExpr)f.apply(x, y);
		}).of(expr(e1), expr(e2));
	}

	@Override
	public BoolExpr exclusivePair(Event load, Event store) {
		return exclusivePair.of(expr(load), expr(store));
	}

	private interface UnaryPredicate {
		BoolExpr of(Expr operand);
	}

	private interface BinaryPredicate {
		BoolExpr of(Expr first, Expr second);
	}

	private interface UnaryFunction {
		IntExpr of(Expr operand);
	}

	private interface BinaryFunction {
		IntExpr of(Expr first, Expr second);
	}

	private Expr expr(Event event) {
		return context.mkNumeral(event.getCId(), sortEvent);
	}
}
