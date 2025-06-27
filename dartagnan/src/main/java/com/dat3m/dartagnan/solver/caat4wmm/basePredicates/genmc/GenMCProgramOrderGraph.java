package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class GenMCProgramOrderGraph extends StaticGenMCWMMGraph {

    private Map<Thread, List<EventData>> threadEventsMap;

    @Override
    public int size(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        if (dir == EdgeDirection.OUTGOING) {
            return (threadEventsMap.get(e.getThread()).size() - e.getLocalId()) - 1;
        } else {
            return e.getLocalId() - 1;
        }
    }

    @Override
    public boolean contains(EventData first, EventData second) {
        return first.getThread() == second.getThread() && second.getLocalId() > first.getLocalId();
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        this.threadEventsMap = trace.getThreadEventsMap();
        size = 0;
        for (List<EventData> threadEvents : threadEventsMap.values()) {
            size += ((threadEvents.size() - 1) * threadEvents.size()) >> 1;
        }
    }

    @Override
    public List<EventData> inEvents(EventData event) {
        List<EventData> threadEvents = trace.getThreadEventsMap().get(event.getThread());
        return threadEvents.subList(0, event.getLocalId());
    }

    @Override
    public List<EventData> outEvents(EventData event) {
        List<EventData> threadEvents = trace.getThreadEventsMap().get(event.getThread());
        return threadEvents.subList(event.getLocalId() + 1, threadEvents.size());
    }

    @Override
    public Stream<Edge> edgeStream() {
        return IntStream.range(0, domain.size())
                .mapToObj(i -> edgeStream(i, EdgeDirection.OUTGOING))
                .flatMap(s -> s);
    }

    @Override
    public Stream<Edge> edgeStream(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        List<EventData> threadEvents = trace.getThreadEventsMap().get(e.getThread());
        if (dir == EdgeDirection.OUTGOING) {
            return threadEvents.subList(e.getLocalId() + 1, threadEvents.size())
                    .stream().map(x -> new Edge(id, x.getId()));
        } else {
            return threadEvents.subList(0, e.getLocalId())
                    .stream().map(x -> new Edge(x.getId(), id));
        }
    }
}
