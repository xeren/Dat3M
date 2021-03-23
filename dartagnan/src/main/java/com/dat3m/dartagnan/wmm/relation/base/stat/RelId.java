package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.Relation;
import java.util.Map;

public class RelId extends StaticRelation {

    public RelId(){
        term = "id";
    }

	@Override
	public boolean[][] test(Map<Relation,boolean[][]> b, int n) {
		boolean[][] r = b.computeIfAbsent(this,k->new boolean[n][n]);
		for(int i=0; i<n; ++i)
			r[i][i] = true;
		return r;
	}

	@Override
	protected void mkMaxTupleSet(){
		for(Event e : program.getCache().getEvents(FilterBasic.get(EType.VISIBLE)))
			addMaxTuple(e,e);
	}
}
