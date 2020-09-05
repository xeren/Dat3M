package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.microsoft.z3.*;
import java.util.*;
import java.util.stream.Stream;

public class EncodeContext {

	private final Context context;
	public final Program program;
	public final Settings settings;
	private final Sort sortEvent;
	private final HashSet<Relation> done = new HashSet<>();
	private final LinkedList<BoolExpr> rule = new LinkedList<>();

	public EncodeContext(Context context, Program program, Settings settings) {
		this.context = context;
		this.program = program;
		this.settings = settings;
		sortEvent = context.mkIntSort();
	}

	public boolean add(Relation self) {
		return done.add(self);
	}

	@FunctionalInterface
	public interface RelationPredicate {
		BoolExpr of(Expr first, Expr second);
	}

	public RelationPredicate of(Relation relation) {
		FuncDecl f = context.mkFuncDecl(relation.getName(), new Sort[]{sortEvent, sortEvent}, context.mkBoolSort());
		return (a,b)->(BoolExpr)f.apply(a, b);
	}

	public void rule(BoolExpr assertion) {
		rule.add(assertion);
	}

	public BoolExpr allRules() {
		return context.mkAnd(rule.toArray(new BoolExpr[0]));
	}

	public Expr event(Event event) {
		return context.mkNumeral(event.getCId(), sortEvent);
	}

	public BoolExpr edge(Relation relation, Event first, Event second) {
		return Utils.edge(relation.getName(), first, second, context);
	}

	public BoolExpr edge(Relation relation, Tuple tuple) {
		return edge(relation, tuple.getFirst(), tuple.getSecond());
	}

	public BoolExpr edge(Relation relation, int iteration, Event first, Event second) {
		return Utils.edge(relation.getName() + "_" + iteration, first, second, context);
	}

	public BoolExpr edge(Relation relation, int iteration, Tuple tuple) {
		return edge(relation, iteration, tuple.getFirst(), tuple.getSecond());
	}

	public IntExpr intVar(Relation relation, Event event) {
		return Utils.intVar(relation.getName(), event, context);
	}

	public IntExpr intCount(Relation relation, Event first, Event second) {
		return Utils.intCount(relation.getName(), first, second, context);
	}

	public IntExpr intCount(Relation relation, Tuple tuple) {
		return intCount(relation, tuple.getFirst(), tuple.getSecond());
	}

	public BoolExpr not(BoolExpr operand) {
		return context.mkNot(operand);
	}

	public BoolExpr and(BoolExpr... operand) {
		return context.mkAnd(operand);
	}

	public BoolExpr and(Collection<BoolExpr> operand) {
		return and(operand.toArray(new BoolExpr[0]));
	}

	public BoolExpr and(Stream<BoolExpr> stream) {
		return and(stream.toArray(BoolExpr[]::new));
	}

	public BoolExpr or(BoolExpr... operand) {
		return context.mkOr(operand);
	}

	public BoolExpr or(Collection<BoolExpr> operand) {
		return or(operand.toArray(new BoolExpr[0]));
	}

	public BoolExpr or(Stream<BoolExpr> stream) {
		return or(stream.toArray(BoolExpr[]::new));
	}

	public BoolExpr implies(BoolExpr premise, BoolExpr conclusion) {
		return context.mkImplies(premise, conclusion);
	}

	public BoolExpr eq(Expr left, Expr right) {
		return context.mkEq(left, right);
	}

	public BoolExpr lt(ArithExpr lower, ArithExpr greater) {
		return context.mkLt(lower, greater);
	}

	@FunctionalInterface
	public interface UnaryBody {
		BoolExpr of(Expr event);
	}

	@FunctionalInterface
	public interface UnaryPattern {
		Pattern of(Expr event);
	}

	public final BoolExpr exists(int depth, UnaryBody body, UnaryPattern... pattern) {
		Expr a = context.mkConst("x" + depth, sortEvent);
		return context.mkExists(new Expr[]{a}, body.of(a), 0,
			Arrays.stream(pattern).map(p->p.of(a)).toArray(Pattern[]::new),
			null, null, null);
	}

	public final BoolExpr forall(int depth, UnaryBody body, UnaryPattern... pattern) {
		Expr a = context.mkConst("x" + depth, sortEvent);
		return context.mkForall(new Expr[]{a}, body.of(a), 0,
			Arrays.stream(pattern).map(p->p.of(a)).toArray(Pattern[]::new),
			null, null, null);
	}

	@FunctionalInterface
	public interface BinaryBody {
		BoolExpr of(Expr first, Expr second);
	}

	@FunctionalInterface
	public interface BinaryPattern {
		Pattern of(Expr first, Expr second);
	}

	public final BoolExpr forall(int depth, BinaryBody body, BinaryPattern... pattern) {
		Expr a = context.mkConst("x" + depth, sortEvent);
		Expr b = context.mkConst("x" + (depth + 1), sortEvent);
		return context.mkForall(new Expr[]{a, b}, body.of(a, b), 0,
			Arrays.stream(pattern).map(p->p.of(a, b)).toArray(Pattern[]::new),
			null, null, null);
	}

	@FunctionalInterface
	public interface TernaryBody {
		BoolExpr of(Expr first, Expr second, Expr third);
	}

	@FunctionalInterface
	public interface TernaryPattern {
		Pattern of(Expr first, Expr second, Expr third);
	}

	public final BoolExpr forall(int depth, TernaryBody body, TernaryPattern... pattern) {
		Expr a = context.mkConst("x" + depth, sortEvent);
		Expr b = context.mkConst("x" + (depth + 1), sortEvent);
		Expr c = context.mkConst("x" + (depth + 2), sortEvent);
		return context.mkForall(new Expr[]{a, b, c}, body.of(a, b, c), 0,
			Arrays.stream(pattern).map(p->p.of(a, b, c)).toArray(Pattern[]::new),
			null, null, null);
	}

	public Pattern pattern(Expr... part) {
		return context.mkPattern(part);
	}
}
