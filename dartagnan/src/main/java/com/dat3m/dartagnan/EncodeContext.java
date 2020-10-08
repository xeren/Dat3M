package com.dat3m.dartagnan;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.*;
import java.util.*;
import java.util.stream.Stream;

public class EncodeContext implements AutoCloseable {

	public final Context context = new Context();
	private final Tactic tactic;
	private final Sort sortEvent;
	private final HashSet<Relation> done = new HashSet<>();
	private final Solver solver;
	private final HashMap<BoolExpr,BoolExpr> track = new HashMap<>();
	private final HashMap<Integer,LinkedList<BoolExpr>> condition = new HashMap<>();

	public EncodeContext() {
		tactic = null;
		sortEvent = context.mkIntSort();
		solver = context.mkSolver();
	}

	public EncodeContext(String tacticName) {
		tactic = context.mkTactic(tacticName);
		sortEvent = context.mkIntSort();
		solver = context.mkSolver(tactic);
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

	public void push() {
		solver.push();
	}

	public void pop() {
		solver.pop();
	}

	/**
	 * Adds a proposition to the current conjunction.
	 * @param assertion
	 * Rule to assert.
	 */
	public void rule(BoolExpr assertion) {
		solver.add(assertion);
	}

	/**
	 * Adds a proposition to the current conjunction and tracks it.
	 * If some combined formula is unsatisfiable because of {@code assertion}, it can return it.
	 * @param assertion
	 * Rule to assert.
	 */
	public void track(BoolExpr assertion, String name) {
		BoolExpr n = context.mkBoolConst(name);
		solver.assertAndTrack(assertion, n);
		track.put(n, assertion);
	}

	public Solver solver()
	{
		Solver s = null == tactic ? context.mkSolver() : context.mkSolver(tactic);
		s.add(solver.getAssertions());
		return s;
	}

	/**
	 * Check if the formula is satisfiable.
	 * @return
	 * There is a model for all current propositions.
	 * @throws RuntimeException
	 * The formula could not be decided.
	 */
	public boolean check() {
		switch(solver.check())
		{
			case SATISFIABLE:
			return true;
			case UNSATISFIABLE:
			return false;
			default:
			throw new RuntimeException("undecidable");
		}
	}

	/**
	 * Tries to satisfy the formula.
	 * @return
	 * A model that satisfies the formula if there is any.
	 */
	public Optional<Model> model() {
		switch(solver.check())
		{
			case SATISFIABLE:
			return Optional.of(solver.getModel());
			case UNSATISFIABLE:
			return Optional.empty();
			default:
			throw new RuntimeException("undecidable");
		}
	}

	public Optional<BoolExpr[]> unsatisfiableCore() {
		switch(solver.check())
		{
			case SATISFIABLE:
			return Optional.empty();
			case UNSATISFIABLE:
			return Optional.of(Arrays.stream(solver.getUnsatCore()).map(track::get).toArray(BoolExpr[]::new));
			default:
			throw new RuntimeException("undecidable");
		}
	}

	public Expr event(Event event) {
		return context.mkNumeral(event.getCId(), sortEvent);
	}

	/**
	 * In a directed acyclic graph, each thread has to choose some path from start to end.
	 * @param event
	 * Compiled event.
	 * @return
	 * Proposition that {code event} is executed in a modelled execution.
	 */
	public BoolExpr exec(Event event) {
		return context.mkBoolConst("exec " + event.getCId());
	}

	/**
	 * The control flow of a program is a graph deciding which statements of the program should be executed.
	 * @param event
	 * Compiled event.
	 * @return
	 * Proposition that this event is included in the execution's control flow.
	 */
	public BoolExpr cf(Event event) {
		return context.mkBoolConst("cf " + event.getCId());
	}

	/**
	 * The control flow graph can join in any point.
	 * It must reach a predecessor for which there may be several candidates.
	 * @param target
	 * Event to add a condition.
	 * @param condition
	 * Proposition implying the control flow reaching this event.
	 */
	public void condition(Event target, BoolExpr condition) {
		this.condition.compute(target.getCId(), (k,v)->{if(null==v)v = new LinkedList<>();v.add(condition);return v;});
	}

	public BoolExpr condition(Event target) {
		return or(condition.getOrDefault(target.getCId(), new LinkedList<>()));
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

	@Override
	public void close() {
		context.close();
	}
}
