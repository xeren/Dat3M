package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.Filter;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelSetIdentity extends StaticRelation {

    protected Filter filter;

    public static String makeTerm(Filter filter){
        return "[" + filter + "]";
    }

    public RelSetIdentity(Filter filter) {
        this.filter = filter;
        term = makeTerm(filter);
    }

    public RelSetIdentity(Filter filter, String name) {
        super(name);
        this.filter = filter;
        term = makeTerm(filter);
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Event e : program.getCache().getEvents(filter)){
                maxTupleSet.add(new Tuple(e, e));
            }
        }
        return maxTupleSet;
    }
}
