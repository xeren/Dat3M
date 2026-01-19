package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class GenMCProgramOrderGraph extends StaticGenMCWMMGraph {

    private Map<Thread, List<EventData>> threadEventsMap;

    @Override
    public int size(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (dir == EdgeDirection.OUTGOING) {
            return (threadEventsMap.get(e.getThread()).size() - e.getLocalId()) - 1;
        } else {
            return e.getLocalId() - 1;
        }
    }

    @Override
    public boolean contains(final EventData first, final EventData second) {
        return first.getThread() == second.getThread() && second.getLocalId() > first.getLocalId();
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        this.threadEventsMap = trace.getThreadEventsMap();
        size = -1;
    }

    private void computeSize() {
        size = 0;
        for (final List<EventData> threadEvents : threadEventsMap.values()) {
            size += ((threadEvents.size() - 1) * threadEvents.size()) >> 1;
        }
    }

    @Override
    public int size() {
        if (size == -1) {
            computeSize();
        }
        return size;
    }

    @Override
    public int getEstimatedSize() { return size(); }

    @Override
    public void forwardPropagate(final Collection<EventData> added) {
        size = -1;
    }

    @Override
    public List<EventData> inEvents(final EventData event) {
        final List<EventData> threadEvents = threadEventsMap.get(event.getThread());
        return new ArrayList<>(threadEvents.subList(0, event.getLocalId()));
    }

    @Override
    public List<EventData> outEvents(final EventData event) {
        final List<EventData> threadEvents = threadEventsMap.get(event.getThread());
        return new ArrayList<>(threadEvents.subList(event.getLocalId() + 1, threadEvents.size()));
    }

    @Override
    public Stream<Edge> edgeStream() {
        return IntStream.range(0, domain.size())
                .mapToObj(i -> edgeStream(i, EdgeDirection.OUTGOING))
                .flatMap(s -> s);
    }

    @Override
    public Stream<Edge> edgeStream(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        final List<EventData> threadEvents = threadEventsMap.get(e.getThread());
        if (dir == EdgeDirection.OUTGOING) {
            return threadEvents.subList(e.getLocalId() + 1, threadEvents.size())
                    .stream().map(x -> new Edge(id, x.getId()));
        } else {
            return threadEvents.subList(0, e.getLocalId())
                    .stream().map(x -> new Edge(x.getId(), id));
        }
    }
}
