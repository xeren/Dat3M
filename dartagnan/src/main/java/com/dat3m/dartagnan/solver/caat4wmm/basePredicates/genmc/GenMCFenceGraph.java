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

    public GenMCFenceGraph(final Filter fenceFilter) {
        this.fenceFilter = fenceFilter;
    }

    @Override
    public boolean containsById(final int id1, final int id2) {
        final EventData a = getEvent(id1);
        final EventData b = getEvent(id2);
        final int localId1;
        final int localId2;
        if (a.getThread() != b.getThread() || (localId1 = a.getLocalId()) >= (localId2 = b.getLocalId()) || !a.isMemoryEvent() || !b.isMemoryEvent()) {
            return false;
        }

        //TODO: We might want to employ binary search instead of linear search
        return threadFencesMap.get(a.getThread()).stream()
                .anyMatch(fence -> localId1 < fence.getLocalId() && fence.getLocalId() < localId2);
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        threadFencesMap = new HashMap<>();
        for (final Thread t : trace.getThreads()) {
            threadFencesMap.put(t, new ArrayList<>());
        }

        trace.getElements().stream().filter(e -> fenceFilter.apply(e.getEvent()))
                .forEach(fence -> threadFencesMap.get(fence.getThread()).add(fence));

        for (final List<EventData> fenceList : threadFencesMap.values()) {
            fenceList.sort(Comparator.comparingInt(EventData::getLocalId));
        }
        super.autoComputeSize();
    }

    @Override
    public void forwardPropagate(final Collection<EventData> added) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Stream<Edge> edgeStream() {
        return trace.getThreadEventsMap().entrySet().stream()
                .flatMap(x -> {
                    List<EventData> fences = threadFencesMap.get(x.getKey());
                    if (fences.isEmpty()) {
                        return Stream.empty();
                    }
                    final int lastId = fences.get(fences.size() - 1).getLocalId();
                    return x.getValue().subList(0, lastId).stream().filter(EventData::isMemoryEvent);
                })
                .flatMap(x -> edgeStream(x.getId(), EdgeDirection.OUTGOING));
    }

    @Override
    public Stream<Edge> edgeStream(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (!e.isMemoryEvent()) {
            return Stream.empty();
        }
        final List<EventData> threadEvents = trace.getThreadEventsMap().get(e.getThread());
        if (dir == EdgeDirection.OUTGOING) {
            final EventData fence = getNextFence(e);
            return fence == null ? Stream.empty() :
                    threadEvents.subList(fence.getLocalId() + 1, threadEvents.size()).stream()
                            .filter(EventData::isMemoryEvent).map(x -> new Edge(id, x.getId()));
        } else {
            final EventData fence = getPreviousFence(e);
            return fence == null ? Stream.empty() :
                    threadEvents.subList(0, fence.getLocalId()).stream()
                            .filter(EventData::isMemoryEvent).map(x -> new Edge(x.getId(), id));
        }
    }

    @Override
    public int size(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (!e.isMemoryEvent()) {
            return 0;
        }
        final int size = 0;
        if (dir == EdgeDirection.OUTGOING) {
            final EventData closestFence = getNextFence(e);
            if (closestFence == null) {
                return 0;
            }
            List<EventData> eventsAfterFence = trace.getThreadEventsMap().get(e.getThread());
            eventsAfterFence = eventsAfterFence.subList(closestFence.getLocalId() + 1, eventsAfterFence.size());
            return (int)eventsAfterFence.stream().filter(EventData::isMemoryEvent).count();
        } else if (dir == EdgeDirection.INGOING) {
            final EventData closestFence = getPreviousFence(e);
            if (closestFence == null) {
                return 0;
            }
            List<EventData> eventsBeforeFence = trace.getThreadEventsMap().get(e.getThread());
            eventsBeforeFence = eventsBeforeFence.subList(0, closestFence.getLocalId());
            return (int) eventsBeforeFence.stream().filter(EventData::isMemoryEvent).count();
        }
        return size;
    }

    public EventData getNextFence(final EventData e) {
        final List<EventData> fences = threadFencesMap.get(e.getThread());
        if (fences.isEmpty()) {
            return null;
        }

        EventData closestFence = null;
        for (int i = fences.size() - 1; i >= 0; i--) {
            final EventData fence = fences.get(i);
            if (fence.getLocalId() > e.getLocalId()) {
                closestFence = fence;
            } else {
                break;
            }
        }
        return closestFence;
    }

    public EventData getPreviousFence(final EventData e) {
        final List<EventData> fences = threadFencesMap.get(e.getThread());
        if (fences.isEmpty()) {
            return null;
        }


        EventData closestFence = null;
        for (final EventData fence : fences) {
            if (fence.getLocalId() < e.getLocalId()) {
                closestFence = fence;
            } else {
                break;
            }
        }
        return closestFence;
    }

}
