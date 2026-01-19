package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

// NOTE: Unlike RelLoc, this graph is reflexive!
public class GenMCLocationGraph extends StaticGenMCWMMGraph {

    private Map<Object, List<EventData>> addrEventsMap;

    @Override
    public boolean contains(final EventData first, final EventData second) {
        return first.getAccessedAddress() != null && first.getAccessedAddress().equals(second.getAccessedAddress());
    }

    @Override
    public int size(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        return e.isMemoryEvent() ? addrEventsMap.get(e.getAccessedAddress()).size() : 0;
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        addrEventsMap = new HashMap<>();
        for (final EventData event : trace.getElements()) {
            final Object address = event.getAccessedAddress();
            if (address != null) {
                addrEventsMap.computeIfAbsent(address, a -> new ArrayList<>()).add(event);
            }
        }
        for (final List<EventData> addrSet : addrEventsMap.values()) {
            size += addrSet.size() * addrSet.size();
        }
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
        return addrEventsMap.get(event.getAccessedAddress());
    }

    @Override
    public Stream<Edge> edgeStream() {
        return addrEventsMap.values().stream().flatMap(Collection::stream)
                .flatMap(x -> edgeStream(x.getId(), EdgeDirection.OUTGOING));
    }

    @Override
    public Stream<Edge> edgeStream(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (!e.isMemoryEvent()) {
            return Stream.empty();
        }
        final Function<EventData, Edge> edgeMapping = dir == EdgeDirection.OUTGOING ?
                (x -> new Edge(id, x.getId())) : (x -> new Edge(x.getId(), id));
        return addrEventsMap.get(e.getAccessedAddress()).stream().map(edgeMapping);
    }

}
