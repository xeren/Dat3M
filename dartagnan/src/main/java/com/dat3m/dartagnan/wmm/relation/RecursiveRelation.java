package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 * @author Florian Furbach
 */
public class RecursiveRelation extends Relation {

	private Relation r1;

	public RecursiveRelation(String name) {
		super(name);
		term = name;
	}

	public static String makeTerm(String name) {
		return name;
	}

	public void initialise() {
		super.initialise();
		r1.initialise();
	}

	public void setConcreteRelation(Relation r1) {
		r1.isRecursive = true;
		r1.setName(name);
		this.r1 = r1;
		this.isRecursive = true;
		this.term = r1.getTerm();
	}

	@Override
	public void update(EncodeContext e, TupleSet s) {
	}

	/**
	 * @return
	 * The tuple set changed by some element.
	 */
	public boolean getMaxTupleSetRecursiveR(EncodeContext context) {
		int old = getMaxTupleSet(context).size();
		maxTupleSet = r1.getMaxTupleSetRecursive(context);
		return old != maxTupleSet.size();
	}

	@Override
	public void addEncodeTupleSet(EncodeContext e, TupleSet s) {
		if(encodeTupleSet != s)
			encodeTupleSet.addAll(s);
	}

	public void addEncodeTupleSetR(EncodeContext context, TupleSet tuples) {
		addEncodeTupleSet(context, tuples);
		r1.addEncodeTupleSet(context, encodeTupleSet);
	}

	public void setRecursiveGroupIdR(int id) {
		forceUpdateRecursiveGroupId = true;
		recursiveGroupId = id;
		r1.setRecursiveGroupId(id);
	}

	@Override
	public int updateRecursiveGroupId(int parentId) {
		if(forceUpdateRecursiveGroupId) {
			forceUpdateRecursiveGroupId = false;
			int r1Id = r1.updateRecursiveGroupId(parentId | recursiveGroupId);
			recursiveGroupId |= r1Id & parentId;
		}
		return recursiveGroupId;
	}

	@Override
	protected void doEncode(EncodeContext context) {
		r1.encode(context);
	}

	@Override
	protected void encodeLFP(EncodeContext context) {
		r1.encodeLFP(context);
	}

	@Override
	protected void encodeIDL(EncodeContext context) {
		r1.encodeIDL(context);
	}

	@Override
	protected void encodeApprox(EncodeContext context) {
		r1.encodeApprox(context);
	}

	public void encodeIterationR(EncodeContext e, int recGroupId, int iteration) {
		r1.encodeIteration(e, recGroupId, iteration);
	}

	public void encodeFinalIteration(EncodeContext e, int iteration) {
		for(Tuple tuple: encodeTupleSet) {
			e.rule(e.eq(e.edge(this, tuple), e.edge(this, iteration, tuple)));
		}
	}

	@Override
	protected void encodeFirstOrder(EncodeContext context) {
		r1.encodeFirstOrder(context);
	}
}
