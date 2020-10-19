package com.dat3m.dartagnan.utils;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.wmm.utils.Flag;
import com.microsoft.z3.*;

public class Encoder {

	private final Context context;

	private int counterNondet = 0;

	public Encoder(Context context) {
		this.context = context;
	}

	public BoolExpr cf(Event event) {
		return context.mkBoolConst("cf(" + event.repr() + ")");
	}

	public BoolExpr exec(Event event) {
		return context.mkBoolConst("exec(" + event.repr() + ")");
	}

	public IntExpr result(Register register, Event event) {
		return context.mkIntConst(register.getName() + "(" + event.repr() + "_result)");
	}

	public IntExpr value(Register register, Event event) {
		return context.mkIntConst(register.getName() + "(" + event.repr() + ")");
	}

	public IntExpr finalValue(Register register) {
		return context.mkIntConst(register.getName() + "_" + register.getThreadId() + "_final");
	}

	public IntExpr finalValue(Address address) {
		return context.mkIntConst("last_val_at_memory_" + address.hashCode());
	}

	public IntExpr memory(Address address) {
		return context.mkIntConst("memory_" + address.hashCode());
	}

	public BoolExpr finalWriter(Event event) {
		return context.mkBoolConst("co_last(" + event.repr() + ")");
	}

	public BoolExpr mkSeqVar(Event read, int i) {
		return context.mkBoolConst("s(rf," + read.repr() + "," + i + ")");
	}

	public BoolExpr edge(String relName, Event e1, Event e2) {
		return context.mkBoolConst(relName + "(" + e1.repr() + "," + e2.repr() + ")");
	}

	public IntExpr intVar(String relName, Event e) {
		return context.mkIntConst(relName + "(" + e.repr() + ")");
	}

	public IntExpr intCount(String relName, Event e1, Event e2) {
		return context.mkIntConst(relName + "(" + e1.repr() + "," + e2.repr() + ")");
	}

	public BoolExpr cycleVar(String relName, Event e) {
		return context.mkBoolConst("Cycle(" + e.repr() + ")(" + relName + ")");
	}

	public BoolExpr cycleEdge(String relName, Event e1, Event e2) {
		return context.mkBoolConst("Cycle:" + relName + "(" + e1.repr() + "," + e2.repr() + ")");
	}

	public BoolExpr exclusivePair(Event load, Event store) {
		return context.mkBoolConst("excl(" + load.getCId() + "," + store.getCId() + ")");
	}

	public BoolExpr mkTrue() {
		return context.mkTrue();
	}

	public BoolExpr mkFalse() {
		return context.mkFalse();
	}

	public BoolExpr mkNot(BoolExpr operand) {
		return context.mkNot(operand);
	}

	public BoolExpr mkAnd(BoolExpr... operand) {
		return context.mkAnd(operand);
	}

	public BoolExpr mkOr(BoolExpr... operand) {
		return context.mkOr(operand);
	}

	public BoolExpr mkEq(BoolExpr first, BoolExpr second) {
		return context.mkEq(first, second);
	}

	public BoolExpr mkImplies(BoolExpr premise, BoolExpr conclusion) {
		return context.mkImplies(premise, conclusion);
	}

	public BoolExpr mkITE(BoolExpr condition, BoolExpr ifTrue, BoolExpr ifFalse) {
		return (BoolExpr)context.mkITE(condition, ifTrue, ifFalse);
	}

	public BoolExpr someBoolean() {
		return context.mkBoolConst("nondet_" + counterNondet++);
	}

	public IntExpr mkInt(int value) {
		return context.mkInt(value);
	}

	public IntExpr mkInt(long value) {
		return context.mkInt(value);
	}

	public BoolExpr mkEq(IntExpr first, IntExpr second) {
		return context.mkEq(first, second);
	}

	public BoolExpr mkDistinct(IntExpr... operand) {
		return context.mkDistinct(operand);
	}

	public BoolExpr mkLe(IntExpr lower, IntExpr greater) {
		return context.mkLe(lower, greater);
	}

	public BoolExpr mkLt(IntExpr lower, IntExpr greater) {
		return context.mkLt(lower, greater);
	}

	public BoolExpr mkGe(IntExpr greater, IntExpr lower) {
		return context.mkGe(greater, lower);
	}

	public BoolExpr mkGt(IntExpr greater, IntExpr lower) {
		return context.mkGt(greater, lower);
	}

	public IntExpr mkAdd(IntExpr first, IntExpr second) {
		return (IntExpr)context.mkAdd(first, second);
	}

	public IntExpr mkSub(IntExpr minuend, IntExpr subtrahend) {
		return (IntExpr)context.mkSub(minuend, subtrahend);
	}

	public IntExpr mkMul(IntExpr first, IntExpr second) {
		return (IntExpr)context.mkMul(first, second);
	}

	public IntExpr mkDiv(IntExpr numerator, IntExpr denominator) {
		return (IntExpr)context.mkDiv(numerator, denominator);
	}

	public IntExpr mkMod(IntExpr numerator, IntExpr denominator) {
		return context.mkMod(numerator, denominator);
	}

	public IntExpr mkITE(BoolExpr condition, IntExpr ifTrue, IntExpr ifFalse) {
		return (IntExpr)context.mkITE(condition, ifTrue, ifFalse);
	}

	public IntExpr someInt() {
		return context.mkIntConst("nondet_" + counterNondet++);
	}

	public BitVecExpr mkInt2BV(int precision, IntExpr operand) {
		return context.mkInt2BV(precision, operand);
	}

	public IntExpr mkBV2Int(BitVecExpr operand, boolean sign) {
		return context.mkBV2Int(operand, sign);
	}

	public BitVecExpr mkBVAND(BitVecExpr first, BitVecExpr second) {
		return context.mkBVAND(first, second);
	}

	public BitVecExpr mkBVOR(BitVecExpr first, BitVecExpr second) {
		return context.mkBVOR(first, second);
	}

	public BitVecExpr mkBVXOR(BitVecExpr first, BitVecExpr second) {
		return context.mkBVXOR(first, second);
	}

	public BitVecExpr mkBVSHL(BitVecExpr first, BitVecExpr second) {
		return context.mkBVSHL(first, second);
	}

	public BitVecExpr mkBVLSHR(BitVecExpr first, BitVecExpr second) {
		return context.mkBVLSHR(first, second);
	}

	public BitVecExpr mkBVASHR(BitVecExpr first, BitVecExpr second) {
		return context.mkBVASHR(first, second);
	}

	public BoolExpr armUnpredictableBehavior() {
		return Flag.ARM_UNPREDICTABLE_BEHAVIOUR.repr(context);
	}
}
