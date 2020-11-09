package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.Computation;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;
import java.util.ListIterator;

public class RelPo extends StaticRelation {

    private FilterAbstract filter;

    public RelPo(){
        this(false);
    }

    public RelPo(boolean includeLocalEvents){
        if(includeLocalEvents){
            term = "_po";
            filter = FilterBasic.get(EType.ANY);
        } else {
            term = "po";
            filter = FilterBasic.get(EType.VISIBLE);
        }
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Thread t : program.getThreads()){
                List<Event> events = t.getCache().getEvents(filter);

                ListIterator<Event> it1 = events.listIterator();
                while(it1.hasNext()){
                    Event e1 = it1.next();
                    ListIterator<Event> it2 = events.listIterator(it1.nextIndex());
                    while(it2.hasNext()){
                        maxTupleSet.add(new Tuple(e1, it2.next()));
                    }
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
        computation.forEachThread(t->{
            for(int i = 0; i < t.size(); ++i) {
                for(int j = 0; j < i; ++j) {
                    r.addMax(t.get(j), t.get(i));
                }
            }
        });
        return r;
    }
}
