package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
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
	protected void mkMaxTupleSet(){
            for(Thread t : program.getThreads()){
                List<Event> events = t.getCache().getEvents(filter);

                ListIterator<Event> it1 = events.listIterator();
                while(it1.hasNext()){
                    Event e1 = it1.next();
                    ListIterator<Event> it2 = events.listIterator(it1.nextIndex());
                    while(it2.hasNext()){
					addMaxTuple(e1,it2.next(),true);
                    }
                }
            }
	}
}
