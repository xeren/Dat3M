package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.stream.Stream;

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
	public void update(ProgramCache p, TupleSet s) {
	}

	/**
	 * @return
	 * The tuple set changed by some element.
	 */
	public boolean getMaxTupleSetRecursiveR(ProgramCache program) {
		int old = getMaxTupleSet(program).size();
		maxTupleSet = r1.getMaxTupleSetRecursive(program);
		return old != maxTupleSet.size();
	}

	@Override
	public void addEncodeTupleSet(ProgramCache p, TupleSet s) {
		if(encodeTupleSet != s)
			encodeTupleSet.addAll(s);
	}

	public void addEncodeTupleSetR(ProgramCache program, TupleSet tuples) {
		addEncodeTupleSet(program, tuples);
		r1.addEncodeTupleSet(program, encodeTupleSet);
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
	protected void doEncode(EncodeContext e, ProgramCache p, Mode m) {
		r1.encode(e, p, m);
	}

	@Override
	protected void encodeLFP(EncodeContext e, ProgramCache p) {
		r1.encodeLFP(e, p);
	}

	@Override
	protected void encodeIDL(EncodeContext e, ProgramCache p) {
		r1.encodeIDL(e, p);
	}

	@Override
	protected Stream<Clause> termFO(Counter t, int a, int b) {
		return r1.nameFO(t, a, b);
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
		r1.encodeApprox(e, p);
	}

	public void encodeIterationR(EncodeContext e, ProgramCache p, int recGroupId, int iteration) {
		r1.encodeIteration(e, p, recGroupId, iteration);
	}

	public void encodeFinalIteration(EncodeContext e, int iteration) {
		for(Tuple tuple: encodeTupleSet) {
			e.rule(e.eq(e.edge(this, tuple), e.edge(this, iteration, tuple)));
		}
	}
}
