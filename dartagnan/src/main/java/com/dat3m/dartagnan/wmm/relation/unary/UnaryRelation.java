package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.dat3m.dartagnan.wmm.relation.Relation;

/**
 * @author Florian Furbach
 */
public abstract class UnaryRelation extends Relation {

	protected Relation r1;

	UnaryRelation(Relation r1) {
		this.r1 = r1;
	}

	UnaryRelation(Relation r1, String name) {
		super(name);
		this.r1 = r1;
	}

	protected abstract void update(EncodeContext context, TupleSet set, TupleSet first);

	@Override
	protected void update(EncodeContext e, TupleSet s) {
		update(e, s, r1.getMaxTupleSet(e));
	}

	@Override
	public int updateRecursiveGroupId(int parentId) {
		if(recursiveGroupId == 0 || forceUpdateRecursiveGroupId) {
			forceUpdateRecursiveGroupId = false;
			int r1Id = r1.updateRecursiveGroupId(parentId | recursiveGroupId);
			recursiveGroupId |= r1Id & parentId;
		}
		return recursiveGroupId;
	}

	@Override
	public void initialise() {
		super.initialise();
		if(recursiveGroupId > 0) {
			throw new RuntimeException("Recursion is not implemented for " + this.getClass().getName());
		}
	}

	@Override
	protected void doEncode(EncodeContext context) {
		r1.encode(context);
		super.doEncode(context);
	}
}
