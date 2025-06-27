package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

// NOTE: Unlike RelLoc, this graph is reflexive!
public class GenMCLocationGraph extends StaticGenMCWMMGraph {

    private Map<BigInteger, List<EventData>> addrEventsMap;

    @Override
    public boolean contains(EventData first, EventData second) {
        return first.isMemoryEvent() && second.isMemoryEvent() && first.getAccessedAddress().equals(second.getAccessedAddress());
    }

    @Override
    public int size(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        return e.isMemoryEvent() ? addrEventsMap.get(e.getAccessedAddress()).size() : 0;
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        addrEventsMap = trace.getElements().stream()
                .filter(EventData::isMemoryEvent)
                .collect(groupingBy(EventData::getAccessedAddress));
        for (List<EventData> addrSet : addrEventsMap.values()) {
            size += addrSet.size() * addrSet.size();
        }
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
        return addrEventsMap.get(event.getAccessedAddress());
    }

    @Override
    public Stream<Edge> edgeStream() {
        return addrEventsMap.values().stream().flatMap(Collection::stream)
                .flatMap(x -> edgeStream(x.getId(), EdgeDirection.OUTGOING));
    }

    @Override
    public Stream<Edge> edgeStream(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        if (!e.isMemoryEvent()) {
            return Stream.empty();
        }
        Function<EventData, Edge> edgeMapping = dir == EdgeDirection.OUTGOING ?
                (x -> new Edge(id, x.getId())) : (x -> new Edge(x.getId(), id));
        return addrEventsMap.get(e.getAccessedAddress()).stream().map(edgeMapping);
    }

}
