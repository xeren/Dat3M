package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import java.util.stream.Stream;

public class RelEmpty extends Relation {

	public RelEmpty(String name) {
		super(name);
		term = name;
	}

	@Override
	protected void update(ProgramCache p, TupleSet s) {
	}

	@Override
	protected void encodeApprox(EncodeContext e, ProgramCache p) {
	}

	@Override
	protected Stream<Clause> termFO(Counter t, int a, int b) {
		return Stream.empty();
	}
}
