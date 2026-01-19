package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class GenMCInternalGraph extends StaticGenMCWMMGraph {
    private Map<Thread, List<EventData>> threadEventsMap;

    @Override
    public boolean contains(final EventData first, final EventData second) {
        return first.getThread() == second.getThread();
    }

    @Override
    public int size(final int id, final EdgeDirection dir) {
        return threadEventsMap.get(getEvent(id).getThread()).size();
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
        return threadEventsMap.get(event.getThread());
    }

    @Override
    public Stream<Edge> edgeStream() {
        return IntStream.range(0, domain.size())
                .mapToObj(i -> edgeStream(i, EdgeDirection.OUTGOING)).flatMap(s -> s);
    }

    @Override
    public Stream<Edge> edgeStream(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        final Function<EventData, Edge> edgeMapping = dir == EdgeDirection.OUTGOING ?
                (x -> new Edge(id, x.getId())) : (x -> new Edge(x.getId(), id));

        return threadEventsMap.get(e.getThread()).stream().map(edgeMapping);
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        threadEventsMap = trace.getThreadEventsMap();
        size = threadEventsMap.values().stream().mapToInt(x -> x.size() * x.size()).sum();
    }

    @Override
    public void forwardPropagate(final Collection<EventData> added) {
        size = threadEventsMap.values().stream().mapToInt(x -> x.size() * x.size()).sum();
    }
}
