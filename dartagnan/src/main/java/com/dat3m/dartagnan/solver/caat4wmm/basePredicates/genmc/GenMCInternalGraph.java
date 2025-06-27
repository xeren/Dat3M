package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class GenMCInternalGraph extends StaticGenMCWMMGraph {
    private Map<Thread, List<EventData>> threadEventsMap;

    @Override
    public boolean contains(EventData first, EventData second) {
        return first.getThread() == second.getThread();
    }

    @Override
    public int size(int id, EdgeDirection dir) {
        return threadEventsMap.get(getEvent(id).getThread()).size();
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
        return threadEventsMap.get(event.getThread());
    }

    @Override
    public Stream<Edge> edgeStream() {
        return IntStream.range(0, domain.size())
                .mapToObj(i -> edgeStream(i, EdgeDirection.OUTGOING)).flatMap(s -> s);
    }

    @Override
    public Stream<Edge> edgeStream(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        Function<EventData, Edge> edgeMapping = dir == EdgeDirection.OUTGOING ?
                (x -> new Edge(id, x.getId())) : (x -> new Edge(x.getId(), id));

        return threadEventsMap.get(e.getThread()).stream().map(edgeMapping);
    }

    @Override
    public void repopulate() {
        if (!isRepopulateAllowed()) return;
        threadEventsMap = trace.getThreadEventsMap();
        size = threadEventsMap.values().stream().mapToInt(x -> x.size() * x.size()).sum();
    }


}
