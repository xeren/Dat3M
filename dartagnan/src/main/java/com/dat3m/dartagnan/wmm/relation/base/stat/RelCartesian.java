package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.Computation;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.ArrayList;
import java.util.List;

public class RelCartesian extends StaticRelation {
    private FilterAbstract filter1;
    private FilterAbstract filter2;

    public static String makeTerm(FilterAbstract filter1, FilterAbstract filter2){
        return "(" + filter1 + "*" + filter2 + ")";
    }

    public RelCartesian(FilterAbstract filter1, FilterAbstract filter2) {
        this.filter1 = filter1;
        this.filter2 = filter2;
        this.term = makeTerm(filter1, filter2);
    }

    public RelCartesian(FilterAbstract filter1, FilterAbstract filter2, String name) {
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

    @Override
    public Computation.Relation register(Computation computation) {
        if(computation.relation.containsKey(this))
            return computation.relation.get(this);
        Computation.Relation r = new Computation.Relation();
        computation.relation.put(this, r);
        ArrayList<com.dat3m.dartagnan.wmm.Event> second = new ArrayList<>();
        computation.forEach(x->{if(filter2.filter(x))second.add(x);});
        computation.forEach(x->{if(filter1.filter(x))second.forEach(y->r.addMax(x,y));});
        return r;
    }
}
