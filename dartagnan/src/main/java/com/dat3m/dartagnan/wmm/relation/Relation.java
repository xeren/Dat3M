package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.HashSet;
import java.util.Set;

/**
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
		this.maxTupleSet = null;
		encodeTupleSet = new TupleSet();
	}

	protected abstract void update(ProgramCache program, TupleSet set);

	public TupleSet getMaxTupleSet(ProgramCache program) {
		if(null == maxTupleSet) {
			maxTupleSet = new TupleSet();
			update(program, maxTupleSet);
		}
		return maxTupleSet;
	}

	public TupleSet getMaxTupleSetRecursive(ProgramCache program) {
		return getMaxTupleSet(program);
	}

	public TupleSet getEncodeTupleSet() {
		return encodeTupleSet;
	}

	public void addEncodeTupleSet(ProgramCache program, TupleSet tuples) {
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

	protected void encodeLFP(EncodeContext context, ProgramCache program) {
		encodeApprox(context, program);
	}

	protected void encodeIDL(EncodeContext context, ProgramCache program) {
		encodeApprox(context, program);
	}

	/**
	 * Describes this relation's content using first order logic.
	 * Proposes that this relation contains only those tuples according to its semantics.
	 * @param context
	 * Utility used to create propositions and to specialize the encoding for the current program.
	 */
	protected abstract void encodeFirstOrder(EncodeContext context, ProgramCache program);

	protected abstract void encodeApprox(EncodeContext context, ProgramCache program);

	public void encodeIteration(EncodeContext context, ProgramCache program, int recGroupId, int iteration) {
	}

	protected void doEncode(EncodeContext context, ProgramCache cache, Mode mode) {
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
			case FO:
				encodeFirstOrder(context, cache);
				break;
			default:
				encodeApprox(context, cache);
		}
	}

}
