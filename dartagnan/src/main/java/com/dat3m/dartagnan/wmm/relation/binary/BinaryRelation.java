package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
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

	protected abstract void update(EncodeContext context, TupleSet set, TupleSet first, TupleSet second);

	@Override
	protected void update(EncodeContext e, TupleSet s) {
		update(e, s, r1.getMaxTupleSet(e), r2.getMaxTupleSet(e));
	}

	@Override
	public TupleSet getMaxTupleSetRecursive(EncodeContext e) {
		if(recursiveGroupId > 0 && maxTupleSet != null) {
			update(e, maxTupleSet, r1.getMaxTupleSetRecursive(e), r2.getMaxTupleSetRecursive(e));
			return maxTupleSet;
		}
		return getMaxTupleSet(e);
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
	public void initialise() {
		super.initialise();
		lastEncodedIteration = -1;
	}

	@Override
	public void addEncodeTupleSet(EncodeContext e, TupleSet s) {
		TupleSet activeSet = new TupleSet();
		activeSet.addAll(s);
		activeSet.removeAll(encodeTupleSet);
		encodeTupleSet.addAll(activeSet);
		activeSet.retainAll(maxTupleSet);
		if(!activeSet.isEmpty()) {
			r1.addEncodeTupleSet(e, activeSet);
			r2.addEncodeTupleSet(e, activeSet);
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
