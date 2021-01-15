package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Visible;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;
import java.util.ListIterator;

public class RelInt extends StaticRelation {

    public RelInt(){
        term = "int";
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Thread t : program.getThreads()) {
                List<Visible> events = t.getCache().getEvents(Visible.class);
                ListIterator<Visible> it1 = events.listIterator();
                while (it1.hasNext()) {
                    Visible e1 = it1.next();
                    ListIterator<Visible> it2 = events.listIterator(it1.nextIndex());
                    while (it2.hasNext()) {
                        Visible e2 = it2.next();
                        maxTupleSet.add(new Tuple(e1, e2));
                        maxTupleSet.add(new Tuple(e2, e1));
                    }
                }
            }
        }
        return maxTupleSet;
    }
}
