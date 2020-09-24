package com.dat3m.dartagnan;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.*;
import java.util.*;
import java.util.stream.Stream;

public class EncodeContext {

	public final Context context;
	private final Sort sortEvent;
	private final HashSet<Relation> done = new HashSet<>();
	private final LinkedList<BoolExpr> rule = new LinkedList<>();

	public EncodeContext(Context context) {
		this.context = context;
		sortEvent = context.mkIntSort();
	}

	public boolean add(Relation self) {
		return done.add(self);
	}

	@FunctionalInterface
	public interface RelationPredicate {
		/**
		 * Creates an atomic formula for a relationship.
		 * Used by the First-Order/Free variant for recursion evaluation.
		 * @param first
		 * Event in this domain set.
		 * @param second
		 * Event in this range set.
		 * @return
		 * Proposition that the pair is contained by this relation.
		 */
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
		BoolExpr result = context.mkAnd(rule.toArray(new BoolExpr[0]));
		rule.clear();
		return result;
	}

	public Expr event(Event event) {
		return context.mkNumeral(event.getCId(), sortEvent);
	}

	public BoolExpr edge(String name, Event first, Event second) {
		return context.mkBoolConst(name + "(" + first.repr() + "," + second.repr() + ")");
	}

	public BoolExpr edge(String name, Tuple tuple) {
		return edge(name, tuple.getFirst(), tuple.getSecond());
	}

	/**
	 * Creates an atomic formula for a relationship.
	 * @param first
	 * Event in this domain set.
	 * @param second
	 * Event in this range set.
	 * @return
	 * Proposition that the specified event pair is contained by this relation.
	 * @see #edge(Relation,Tuple)
	 */
	public BoolExpr edge(Relation relation, Event first, Event second) {
		return edge(relation.getName(), first, second);
	}

	/**
	 * Creates an atomic formula for a relationship.
	 * @param tuple
	 * Pair of events.
	 * @return
	 * Proposition that the pair is contained by this relation.
	 * @see #edge(Relation,Event,Event)
	 */
	public BoolExpr edge(Relation relation, Tuple tuple) {
		return edge(relation, tuple.getFirst(), tuple.getSecond());
	}

	/**
	 * Creates an atomic formula for a relationship.
	 * Used in the Kleene/LFP variant for recursion evaluation.
	 * @param relation
	 * Context of the variable.
	 * @param iteration
	 * Index of the associated version of this relation.
	 * Between 0 and the iteration count of the {@link com.dat3m.dartagnan.wmm.utils.RecursiveGroup} in context.
	 * @param first
	 * Event in this domain set.
	 * @param second
	 * Event in this range set.
	 * @return
	 * Proposition that the pair is contained by this relation's version.
	 * @see #edge(Relation,int,Tuple)
	 */
	public BoolExpr edge(Relation relation, int iteration, Event first, Event second) {
		return edge(relation.getName() + "_" + iteration, first, second);
	}

	/**
	 * Creates an atomic formula for a relationship.
	 * Used in the Kleene/LFP variant for recursion evaluation.
	 * @param relation
	 * Context of the variable.
	 * @param iteration
	 * Index of the associated version of this relation.
	 * Between 0 and the iteration count of the {@link com.dat3m.dartagnan.wmm.utils.RecursiveGroup} in context.
	 * @param tuple
	 * Pair of events.
	 * @return
	 * Proposition that the pair is contained by this relation's version.
	 */
	public BoolExpr edge(Relation relation, int iteration, Tuple tuple) {
		return edge(relation, iteration, tuple.getFirst(), tuple.getSecond());
	}

	/**
	 * Used to describe a total order induced by this relation.
	 * Yields a monotone mapping into the integer order.
	 * @param name
	 * Context of the mapping.
	 * Usually only variables of similar name are put in relation.
	 * @param event
	 * Some event.
	 * @return
	 * Integer equally ordered with respect to other events.
	 */
	public IntExpr intVar(String name, Event event) {
		return context.mkIntConst(name + "(" + event.repr() + ")");
	}

	public IntExpr intCount(String name, Event first, Event second) {
		return context.mkIntConst(name + "(" + first.repr() + "," + second.repr() + ")");
	}

	/**
	 * Associates an integer with a pair of events in a relation.
	 * Used by Integer Difference Logic (IDL) encoding for recursion evaluation.
	 * @param relation
	 * Context for this integer value.
	 * @param first
	 * Event in the domain set of {@code relation}.
	 * @param second
	 * Event in the range set of {@code relation}.
	 * @return
	 * Integer.
	 */
	public IntExpr intCount(Relation relation, Event first, Event second) {
		return intCount(relation.getName(), first, second);
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

	public BoolExpr distinct(Collection<?extends Expr> element) {
		return context.mkDistinct(element.toArray(new Expr[0]));
	}

	public BoolExpr lt(ArithExpr lower, ArithExpr greater) {
		return context.mkLt(lower, greater);
	}

	public IntExpr zero() {
		return context.mkInt(0);
	}

	public IntExpr one() {
		return context.mkInt(1);
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
