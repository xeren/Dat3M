package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Init;
import com.dat3m.dartagnan.program.event.Visible;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;
import java.util.ListIterator;

public class RelExt extends StaticRelation {

    public RelExt(){
        term = "ext";
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            List<Thread> threads = program.getThreads();
            ListIterator<Thread> it1 = threads.listIterator();
            while(it1.hasNext()){
                Thread t1 = it1.next();
                ListIterator<Thread> it2 = threads.listIterator(it1.nextIndex());
                while(it2.hasNext()){
                    Thread t2 = it2.next();
                    for(Visible e1 : t1.getCache().getEvents(Visible.class)){
                        for(Visible e2 : t2.getCache().getEvents(Visible.class)){
                            maxTupleSet.add(new Tuple(e1, e2));
                            maxTupleSet.add(new Tuple(e2, e1));
                        }
                    }
                }
                for(Visible e : t1.getCache().getEvents(Visible.class)) {
                    for(Init i : program.getCache().getEvents(Init.class)) {
                        maxTupleSet.add(new Tuple(e, i));
                        maxTupleSet.add(new Tuple(i, e));
                    }
                }
            }
        }
        return maxTupleSet;
    }
}
