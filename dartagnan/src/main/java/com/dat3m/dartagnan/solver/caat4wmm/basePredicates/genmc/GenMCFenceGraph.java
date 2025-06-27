package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;


import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.*;
import java.util.stream.Stream;

public class GenMCFenceGraph extends StaticGenMCWMMGraph {

    private final Filter fenceFilter;
    private Map<Thread, List<EventData>> threadFencesMap;

    public GenMCFenceGraph(Filter fenceFilter) {
        this.fenceFilter = fenceFilter;
    }

    @Override
    public boolean containsById(int id1, int id2) {
        EventData a = getEvent(id1);
        EventData b = getEvent(id2);
        int localId1;
        int localId2;
        if (a.getThread() != b.getThread() || (localId1 = trace.getLocalId(a)) >= (localId2 = trace.getLocalId(b)) || !a.isMemoryEvent() || !b.isMemoryEvent()) {
            return false;
        }

        //TODO: We might want to employ binary search instead of linear search
        return threadFencesMap.get(a.getThread()).stream()
                .anyMatch(fence -> localId1 < trace.getLocalId(fence) && trace.getLocalId(fence) < localId2);
    }

    @Override
    public void repopulate() {
        if (!isRepopulateAllowed()) return;
        threadFencesMap = new HashMap<>();
        for (Thread t : trace.getThreads()) {
            threadFencesMap.put(t, new ArrayList<>());
        }

        trace.getElements().stream().filter(e -> fenceFilter.apply(e.getEvent()))
                .forEach(fence -> threadFencesMap.get(fence.getThread()).add(fence));

        for (List<EventData> fenceList : threadFencesMap.values()) {
            fenceList.sort(Comparator.comparingInt(trace::getLocalId));
        }
        super.autoComputeSize();
    }

    @Override
    public Stream<Edge> edgeStream() {
        return trace.getThreadEventsMap().entrySet().stream()
                .flatMap(x -> {
                    List<EventData> fences = threadFencesMap.get(x.getKey());
                    if (fences.isEmpty()) {
                        return Stream.empty();
                    }
                    int lastId = trace.getLocalId(fences.get(fences.size() - 1));
                    return x.getValue().subList(0, lastId).stream().filter(EventData::isMemoryEvent);
                })
                .flatMap(x -> edgeStream(x.getId(), EdgeDirection.OUTGOING));
    }

    @Override
    public Stream<Edge> edgeStream(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        if (!e.isMemoryEvent()) {
            return Stream.empty();
        }
        List<EventData> threadEvents = trace.getThreadEventsMap().get(e.getThread());
        if (dir == EdgeDirection.OUTGOING) {
            EventData fence = getNextFence(e);
            return fence == null ? Stream.empty() :
                    threadEvents.subList(trace.getLocalId(fence) + 1, threadEvents.size()).stream()
                            .filter(EventData::isMemoryEvent).map(x -> new Edge(id, x.getId()));
        } else {
            EventData fence = getPreviousFence(e);
            return fence == null ? Stream.empty() :
                    threadEvents.subList(0, trace.getLocalId(fence)).stream()
                            .filter(EventData::isMemoryEvent).map(x -> new Edge(x.getId(), id));
        }
    }

    @Override
    public int size(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        if (!e.isMemoryEvent()) {
            return 0;
        }
        int size = 0;
        if (dir == EdgeDirection.OUTGOING) {
            EventData closestFence = getNextFence(e);
            if (closestFence == null) {
                return 0;
            }
            List<EventData> eventsAfterFence = trace.getThreadEventsMap().get(e.getThread());
            eventsAfterFence = eventsAfterFence.subList(trace.getLocalId(closestFence) + 1, eventsAfterFence.size());
            return (int)eventsAfterFence.stream().filter(EventData::isMemoryEvent).count();
        } else if (dir == EdgeDirection.INGOING) {
            EventData closestFence = getPreviousFence(e);
            if (closestFence == null) {
                return 0;
            }
            List<EventData> eventsBeforeFence = trace.getThreadEventsMap().get(e.getThread());
            eventsBeforeFence = eventsBeforeFence.subList(0, trace.getLocalId(closestFence));
            return (int) eventsBeforeFence.stream().filter(EventData::isMemoryEvent).count();
        }
        return size;
    }

    public EventData getNextFence(EventData e) {
        List<EventData> fences = threadFencesMap.get(e.getThread());
        if (fences.isEmpty()) {
            return null;
        }

        EventData closestFence = null;
        for (int i = fences.size() - 1; i >= 0; i--) {
            EventData fence = fences.get(i);
            if (trace.getLocalId(fence) > trace.getLocalId(e)) {
                closestFence = fence;
            } else {
                break;
            }
        }
        return closestFence;
    }

    public EventData getPreviousFence(EventData e) {
        List<EventData> fences = threadFencesMap.get(e.getThread());
        if (fences.isEmpty()) {
            return null;
        }


        EventData closestFence = null;
        for (EventData fence : fences) {
            if (trace.getLocalId(fence) < trace.getLocalId(e)) {
                closestFence = fence;
            } else {
                break;
            }
        }
        return closestFence;
    }

}
