package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;

public class RelFencerel extends StaticRelation {

    private final String fenceName;

    public static String makeTerm(String fenceName){
        return "fencerel(" + fenceName + ")";
    }

    public RelFencerel(String fenceName) {
        this.fenceName = fenceName;
        term = makeTerm(fenceName);
    }

    public String getFenceName() { return fenceName; }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitFences(this, FilterBasic.get(fenceName));
    }

    @Override
    public TupleSet getMinTupleSet(){
        if(minTupleSet == null){
            ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
            minTupleSet = new TupleSet();
            for(Thread t : task.getProgram().getThreads()){
                List<Event> fences = t.getCache().getEvents(FilterBasic.get(fenceName));
                List<Event> memEvents = t.getCache().getEvents(FilterBasic.get(Tag.MEMORY));
                for (Event fence : fences) {
                    int numEventsBeforeFence = (int) memEvents.stream()
                            .mapToInt(Event::getCId).filter(id -> id < fence.getCId())
                            .count();
                    List<Event> eventsBefore = memEvents.subList(0, numEventsBeforeFence);
                    List<Event> eventsAfter = memEvents.subList(numEventsBeforeFence, memEvents.size());

                    for (Event e1 : eventsBefore) {
                        boolean isImpliedByE1 = exec.isImplied(e1, fence);
                        for (Event e2 : eventsAfter) {
                            if (isImpliedByE1 || exec.isImplied(e2, fence)) {
                                minTupleSet.add(new Tuple(e1, e2));
                            }
                        }
                    }
                }
            }
            removeMutuallyExclusiveTuples(minTupleSet);
        }
        return minTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Thread t : task.getProgram().getThreads()){
                List<Event> fences = t.getCache().getEvents(FilterBasic.get(fenceName));
                List<Event> memEvents = t.getCache().getEvents(FilterBasic.get(Tag.MEMORY));
                for (Event fence : fences) {
                    int numEventsBeforeFence = (int) memEvents.stream()
                            .mapToInt(Event::getCId).filter(id -> id < fence.getCId())
                            .count();
                    List<Event> eventsBefore = memEvents.subList(0, numEventsBeforeFence);
                    List<Event> eventsAfter = memEvents.subList(numEventsBeforeFence, memEvents.size());

                    for (Event e1 : eventsBefore) {
                        for (Event e2 : eventsAfter) {
                            maxTupleSet.add(new Tuple(e1, e2));
                        }
                    }
                }
            }
            removeMutuallyExclusiveTuples(maxTupleSet);
        }
        return maxTupleSet;
    }
}
