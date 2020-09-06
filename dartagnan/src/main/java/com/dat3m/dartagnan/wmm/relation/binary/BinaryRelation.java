package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 * @author Florian Furbach
 */
public abstract class BinaryRelation extends Relation {

	protected Relation r1;
	protected Relation r2;

	int lastEncodedIteration = -1;

	BinaryRelation(Relation r1, Relation r2) {
		this.r1 = r1;
		this.r2 = r2;
	}

	BinaryRelation(Relation r1, Relation r2, String name) {
		super(name);
		this.r1 = r1;
		this.r2 = r2;
	}

	protected abstract void update(TupleSet set, TupleSet first, TupleSet second);

	@Override
	protected void update(TupleSet set) {
		update(set, r1.getMaxTupleSet(), r2.getMaxTupleSet());
	}

	@Override
	public TupleSet getMaxTupleSetRecursive() {
		if(recursiveGroupId > 0 && maxTupleSet != null) {
			update(maxTupleSet, r1.getMaxTupleSetRecursive(), r2.getMaxTupleSetRecursive());
			return maxTupleSet;
		}
		return getMaxTupleSet();
	}

	@Override
	public int updateRecursiveGroupId(int parentId) {
		if(recursiveGroupId == 0 || forceUpdateRecursiveGroupId) {
			forceUpdateRecursiveGroupId = false;
			int r1Id = r1.updateRecursiveGroupId(parentId | recursiveGroupId);
			int r2Id = r2.updateRecursiveGroupId(parentId | recursiveGroupId);
			recursiveGroupId |= (r1Id | r2Id) & parentId;
		}
		return recursiveGroupId;
	}

	@Override
	public void initialise(Program program, Context ctx, Settings settings) {
		super.initialise(program, ctx, settings);
		lastEncodedIteration = -1;
	}

	@Override
	public void addEncodeTupleSet(TupleSet tuples) {
		TupleSet activeSet = new TupleSet();
		activeSet.addAll(tuples);
		activeSet.removeAll(encodeTupleSet);
		encodeTupleSet.addAll(activeSet);
		activeSet.retainAll(maxTupleSet);
		if(!activeSet.isEmpty()) {
			r1.addEncodeTupleSet(activeSet);
			r2.addEncodeTupleSet(activeSet);
		}
	}

	@Override
	protected void doEncode(EncodeContext e) {
		r1.encode(e);
		r2.encode(e);
		super.doEncode(e);
	}

	@Override
	protected void encodeLFP(EncodeContext e) {
		if(recursiveGroupId <= 0)
			encodeApprox(e);
	}
}
