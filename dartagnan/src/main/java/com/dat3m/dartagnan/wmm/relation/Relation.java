package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.utils.Settings;
import com.microsoft.z3.*;
import com.dat3m.dartagnan.program.Program;
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

	protected Program program;
	protected Context ctx;

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

	public void initialise(Program program, Context ctx, Settings settings) {
		this.program = program;
		this.maxTupleSet = null;
		encodeTupleSet = new TupleSet();
	}

	public abstract TupleSet getMaxTupleSet();

	public TupleSet getMaxTupleSetRecursive() {
		return getMaxTupleSet();
	}

	public TupleSet getEncodeTupleSet() {
		return encodeTupleSet;
	}

	public void addEncodeTupleSet(TupleSet tuples) {
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
	 * @param context Utility used to create propositions and to specialize the encoding for the current program.
	 */
	public void encode(EncodeContext context) {
		if(context.add(this))
			doEncode(context);
	}

	protected void encodeLFP(EncodeContext context) {
		encodeApprox(context);
	}

	protected void encodeIDL(EncodeContext context) {
		encodeApprox(context);
	}

	/**
	 * Describes this relation's content using first order logic.
	 * Proposes that this relation contains only those tuples according to its semantics.
	 * @param context Utility used to create propositions and to specialize the encoding for the current program.
	 */
	protected abstract void encodeFirstOrder(EncodeContext context);

	protected abstract void encodeApprox(EncodeContext context);

	public void encodeIteration(EncodeContext context, int recGroupId, int iteration) {
	}

	protected void doEncode(EncodeContext e) {
		if(!encodeTupleSet.isEmpty()) {
			Set<Tuple> negations = new HashSet<>(encodeTupleSet);
			negations.removeAll(maxTupleSet);
			for(Tuple tuple: negations)
				e.rule(e.not(e.edge(this, tuple)));
			encodeTupleSet.removeAll(negations);
		}
		if(encodeTupleSet.isEmpty() && !forceDoEncode)
			return;
		switch(e.settings.getMode()) {
			case KLEENE:
				encodeLFP(e);
				break;
			case IDL:
				encodeIDL(e);
				break;
			case FO:
				encodeFirstOrder(e);
				break;
			default:
				encodeApprox(e);
		}
	}

}
