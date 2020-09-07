package com.dat3m.dartagnan.wmm.relation.binary;

import com.dat3m.dartagnan.wmm.ProgramCache;
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

	protected abstract void update(ProgramCache program, TupleSet set, TupleSet first, TupleSet second);

	@Override
	protected void update(ProgramCache p, TupleSet s) {
		update(p, s, r1.getMaxTupleSet(p), r2.getMaxTupleSet(p));
	}

	@Override
	public TupleSet getMaxTupleSetRecursive(ProgramCache p) {
		if(recursiveGroupId > 0 && maxTupleSet != null) {
			update(p, maxTupleSet, r1.getMaxTupleSetRecursive(p), r2.getMaxTupleSetRecursive(p));
			return maxTupleSet;
		}
		return getMaxTupleSet(p);
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
	public void addEncodeTupleSet(ProgramCache p, TupleSet s) {
		TupleSet activeSet = new TupleSet();
		activeSet.addAll(s);
		activeSet.removeAll(encodeTupleSet);
		encodeTupleSet.addAll(activeSet);
		activeSet.retainAll(maxTupleSet);
		if(!activeSet.isEmpty()) {
			r1.addEncodeTupleSet(p, activeSet);
			r2.addEncodeTupleSet(p, activeSet);
		}
	}

	@Override
	protected void doEncode(EncodeContext e, ProgramCache p) {
		r1.encode(e, p);
		r2.encode(e, p);
		super.doEncode(e, p);
	}

	@Override
	protected void encodeLFP(EncodeContext e, ProgramCache p) {
		if(recursiveGroupId <= 0)
			encodeApprox(e, p);
	}
}
