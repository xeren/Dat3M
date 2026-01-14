package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class GenMCExternalGraph extends StaticGenMCWMMGraph {
    private Map<Thread, List<EventData>> threadEventsMap;

    @Override
    public boolean contains(final EventData first, final EventData second) {
        return first.getThread() != second.getThread();
    }

    @Override
    public int size(final int id, final EdgeDirection dir) {
        return domain.size() - threadEventsMap.get(getEvent(id).getThread()).size();
    }

    @Override
    public List<EventData> inEvents(final EventData event) {
        return getEvents(event);
    }

    @Override
    public List<EventData> outEvents(final EventData event) {
        return getEvents(event);
    }

    public List<EventData> getEvents(final EventData event) {
        final List<EventData> events = new ArrayList<>();
        final Thread eventThread = event.getThread();
        for (final Map.Entry<Thread, List<EventData>> entry : threadEventsMap.entrySet()) {
            if (entry.getKey() != eventThread) {
                events.addAll(entry.getValue());
            }
        }
        return events;
    }

    @Override
    public Stream<Edge> edgeStream() {
        return IntStream.range(0, domain.size())
                .mapToObj(e -> edgeStream(e, EdgeDirection.OUTGOING))
                .flatMap(s -> s);
    }

    @Override
    public Stream<Edge> edgeStream(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        final Function<EventData, Edge> edgeMapping = dir == EdgeDirection.OUTGOING ?
                (x -> new Edge(id, x.getId())) : (x -> new Edge(x.getId(), id));

        return threadEventsMap.entrySet().stream()
                .filter(x -> x.getKey() != e.getThread())
                .flatMap(x -> x.getValue().stream())
                .map(edgeMapping);
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        threadEventsMap = trace.getThreadEventsMap();
        final int totalSize = domain.size();
        for (final List<EventData> threadEvents : threadEventsMap.values()) {
            final int threadSize = threadEvents.size();
            size += (totalSize - threadSize) * threadSize;
        }
    }

}
