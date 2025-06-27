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
    public boolean contains(EventData first, EventData second) {
        return first.getThread() != second.getThread();
    }

    @Override
    public int size(int id, EdgeDirection dir) {
        return domain.size() - threadEventsMap.get(getEvent(id).getThread()).size();
    }

    @Override
    public List<EventData> inEvents(EventData event) {
        return getEvents(event);
    }

    @Override
    public List<EventData> outEvents(EventData event) {
        return getEvents(event);
    }

    public List<EventData> getEvents(EventData event) {
        List<EventData> events = new ArrayList<>();
        Thread eventThread = event.getThread();
        for (Map.Entry<Thread, List<EventData>> entry : threadEventsMap.entrySet()) {
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
    public Stream<Edge> edgeStream(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        Function<EventData, Edge> edgeMapping = dir == EdgeDirection.OUTGOING ?
                (x -> new Edge(id, x.getId())) : (x -> new Edge(x.getId(), id));

        return threadEventsMap.entrySet().stream()
                .filter(x -> x.getKey() != e.getThread())
                .flatMap(x -> x.getValue().stream())
                .map(edgeMapping);
    }

    @Override
    public void repopulate() {
        if (!isRepopulateAllowed()) return;
        threadEventsMap = trace.getThreadEventsMap();
        int totalSize = domain.size();
        for (List<EventData> threadEvents : threadEventsMap.values()) {
            int threadSize = threadEvents.size();
            size += (totalSize - threadSize) * threadSize;
        }
    }

}
