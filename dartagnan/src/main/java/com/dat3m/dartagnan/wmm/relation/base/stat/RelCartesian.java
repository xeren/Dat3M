package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.Filter;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;

public class RelCartesian extends StaticRelation {
    private Filter filter1;
    private Filter filter2;

    public static String makeTerm(Filter filter1, Filter filter2){
        return "(" + filter1 + "*" + filter2 + ")";
    }

    public RelCartesian(Filter filter1, Filter filter2) {
        this.filter1 = filter1;
        this.filter2 = filter2;
        this.term = makeTerm(filter1, filter2);
    }

    public RelCartesian(Filter filter1, Filter filter2, String name) {
        super(name);
        this.filter1 = filter1;
        this.filter2 = filter2;
        this.term = makeTerm(filter1, filter2);
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            List<Event> l1 = program.getCache().getEvents(filter1);
            List<Event> l2 = program.getCache().getEvents(filter2);
            for(Event e1 : l1){
                for(Event e2 : l2){
                    maxTupleSet.add(new Tuple(e1, e2));
                }
            }
        }
        return maxTupleSet;
    }
}
