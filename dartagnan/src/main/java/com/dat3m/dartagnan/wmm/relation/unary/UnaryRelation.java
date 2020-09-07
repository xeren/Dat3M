package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.Mode;
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

	protected abstract void update(ProgramCache program, TupleSet set, TupleSet first);

	@Override
	protected void update(ProgramCache p, TupleSet s) {
		update(p, s, r1.getMaxTupleSet(p));
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
	protected void doEncode(EncodeContext e, ProgramCache p, Mode m) {
		r1.encode(e, p, m);
		super.doEncode(e, p, m);
	}
}
