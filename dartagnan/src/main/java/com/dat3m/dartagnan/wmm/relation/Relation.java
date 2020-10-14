package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Symbolic relation over events in an execution of a program.
 * @author Florian Furbach
 */
public abstract class Relation {

	public static boolean PostFixApprox = false;

	protected String name;
	protected String term;

	protected TupleSet maxTupleSet;
	protected TupleSet encodeTupleSet;

	protected int recursiveGroupId = 0;
	protected boolean forceUpdateRecursiveGroupId = false;
	protected boolean isRecursive = false;
	protected boolean forceDoEncode = false;

	public Relation() {
	}

	public Relation(String name) {
		this.name = name;
	}

	public int getRecursiveGroupId() {
		return recursiveGroupId;
	}

	public void setRecursiveGroupId(int id) {
		forceUpdateRecursiveGroupId = true;
		recursiveGroupId = id;
	}

	public int updateRecursiveGroupId(int parentId) {
		return recursiveGroupId;
	}

	public void initialise() {
		maxTupleSet = null;
		encodeTupleSet = new TupleSet();
	}

	/**
	 * Modifies the maximal set of events.
	 * @param cache
	 * Collection of defined events in all branches of an acyclic program.
	 * @param set
	 * Finite set of event pairs associated with this relation.
	 * Modified by this method.
	 */
	protected abstract void update(ProgramCache cache, TupleSet set);

	/**
	 * Over-approximates the union of this relation's contents with respect to any computation.
	 * @param cache
	 * Collection of defined events in all branches of an acyclic program.
	 * @return
	 * Maximal set of event pairs.
	 */
	public TupleSet getMaxTupleSet(ProgramCache cache) {
		if(null == maxTupleSet) {
			maxTupleSet = new TupleSet();
			update(cache, maxTupleSet);
		}
		return maxTupleSet;
	}

	/**
	 * Computes the maximal set of event pairs in this relation.
	 * Recursive relations use this opportunity to refresh their tuple set.
	 * @param cache
	 * Collection of defined events in all branches of an acyclic program.
	 * @return
	 * Updated maximal set of event pairs.
	 */
	public TupleSet getMaxTupleSetRecursive(ProgramCache cache) {
		return getMaxTupleSet(cache);
	}

	/**
	 * @return
	 * Collection of event pairs required to be checked.
	 */
	public TupleSet getEncodeTupleSet() {
		return encodeTupleSet;
	}

	/**
	 * Marks event pairs to be checked by the reasoner.
	 * @param cache
	 * Finite collection of events in an acyclic program.
	 * @param tuples
	 * Collection of pairs to mark.
	 */
	public void addEncodeTupleSet(ProgramCache cache, TupleSet tuples) {
		encodeTupleSet.addAll(tuples);
	}

	public String getName() {
		if(name != null) {
			return name;
		}
		return term;
	}

	public Relation setName(String name) {
		this.name = name;
		return this;
	}

	public String getTerm() {
		return term;
	}

	public boolean getIsNamed() {
		return name != null;
	}

	@Override
	public String toString() {
		if(name != null) {
			return name + " := " + term;
		}
		return term;
	}

	@Override
	public int hashCode() {
		return getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;

		if(obj == null || getClass() != obj.getClass())
			return false;

		return getName().equals(((Relation) obj).getName());
	}

	/**
	 * Describes this relation's contents.
	 * Proposes that this relation contains only those tuples according to its semantics.
	 * @param context
	 * Utility used to create propositions and to specialize the encoding for the current program.
	 * @param cache
	 * Events issued by the tested program.
	 * @param mode
	 * Dialect for the encoding.
	 */
	public void encode(EncodeContext context, ProgramCache cache, Mode mode) {
		if(context.add(this))
			doEncode(context, cache, mode);
	}

	/**
	 * Describes this relation's contents as the least fixed point of a update mapping.
	 * For a number of iterations, membership in that current version of this relation is specified.
	 * @param context
	 * Utility for building expressions and for collecting the constructed rules.
	 * @param cache
	 * Representation of the acyclic program in question.
	 */
	protected void encodeLFP(EncodeContext context, ProgramCache cache) {
		encodeApprox(context, cache);
	}

	/**
	 * Describes this relation's contents with Integer Difference Logic:
	 * In a recursion, in addition to boolean variables denoting the membership of a relationship,
	 * the encoding features an order in which event pairs are added to a model of this relation.
	 * @param context
	 * Utility for building expressions and for collecting the constructed rules.
	 * @param cache
	 * Representation of the acyclic program in question.
	 */
	protected void encodeIDL(EncodeContext context, ProgramCache cache) {
		encodeApprox(context, cache);
	}

	protected interface Counter {
		int next();
	}

	/**
	 * Describes this relation's contents using first order logic.
	 * Proposes that this relation contains only those tuples according to its semantics.
	 * @param context
	 * Utility for building expressions and for collecting the constructed rules.
	 * @param cache
	 * Representation of the acyclic program in question.
	 */
	protected void encodeFirstOrder(EncodeContext context, ProgramCache cache) {
		if(null == name)
			return;

		int[] counter = new int[]{2};
		termFO(()->counter[0]++, 0, 1).forEach(c->consumeFO(context, context.binary(name), counter[0], c));
	}

	protected static void consumeFO(EncodeContext context, EncodeContext.BinaryPredicate name, int counter, Clause c) {
		// formulate equivalence classes
		int[] f = new int[counter];
		f[1] = 1;
		c.free(i->f[i] = i);
		// invariant forall i < f.length: f[i] == i || f[i] < i && f[f[i]] == f[i]
		c.eq((left,right)->{
			int f0 = f[left];
			int f1 = f[right];
			if(f0 < f1) {
				for(int i = f1; i < counter; ++i)
					if(f1 == f[i])
						f[i] = f0;
			} else if(f1 < f0) {
				for(int i = f0; i < counter; ++i)
					if(f0 == f[i])
						f[i] = f1;
			}
		});

		LinkedList<Expr> bound = new LinkedList<>();
		Expr[] b = new Expr[counter];
		int x = 0;
		for(int i = 0; i < counter; ++i)
			if(i == f[i])
				bound.add(b[i] = context.bind(x++));
		for(int i = 0; i < counter; ++i)
			if(0 <= f[i])
				b[i] = b[f[i]];
		LinkedList<BoolExpr> enc = new LinkedList<>();
		c.edge((n,d,r)->enc.add(context.binary(n).of(b[d], b[r])));
		c.set((n,m)->enc.add(context.unary(n).of(b[m])));

		context.ruleForall(bound, enc, name.of(b[0], b[1]));
	}

	public Stream<Clause> nameFO(Counter counter, int domain, int range) {
		if(null == name)
			return termFO(counter, domain, range);
		return Stream.of(Clause.edge(name, domain, range));
	}

	protected abstract Stream<Clause> termFO(Counter counter, int domain, int range);

	/**
	 * Naive description of this relation's contents.
	 * @param context
	 * Utility for building expressions and for collecting the constructed rules.
	 * @param cache
	 * Representation of the acyclic program in question.
	 */
	protected abstract void encodeApprox(EncodeContext context, ProgramCache cache);

	/**
	 * Called when Kleene-style encoding was used.
	 * Creates a series of boolean variables for each pair in question and each iteration .
	 * @param context
	 * Utility for building expressions and for collecting the constructed rules.
	 * @param cache
	 * Representation of the acyclic program in question.
	 * @param recGroupId
	 * Group of recursive relations currently encoded.
	 * @param iteration
	 * Current step to be encoded.
	 */
	public void encodeIteration(EncodeContext context, ProgramCache cache, int recGroupId, int iteration) {
	}

	protected void doEncode(EncodeContext context, ProgramCache cache, Mode mode) {
		if(mode == Mode.FO) {
			encodeFirstOrder(context, cache);
			return;
		}
		// all pairs to be encoded that fall from the over-approximation
		if(!encodeTupleSet.isEmpty()) {
			Set<Tuple> negations = new HashSet<>(encodeTupleSet);
			negations.removeAll(maxTupleSet);
			for(Tuple tuple: negations)
				context.rule(context.not(context.edge(this, tuple)));
			encodeTupleSet.removeAll(negations);
		}
		if(encodeTupleSet.isEmpty() && !forceDoEncode)
			return;
		switch(mode) {
			case KLEENE:
				encodeLFP(context, cache);
				break;
			case IDL:
				encodeIDL(context, cache);
				break;
			default:
				encodeApprox(context, cache);
		}
	}


}
