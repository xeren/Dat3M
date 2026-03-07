package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.event.core.Load;
import com.dat3m.dartagnan.program.event.core.RMWStoreExclusive;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.*;
import java.util.stream.Stream;

public class GenMCLXSXGraph extends StaticGenMCWMMGraph {

    private Map<EventData, EventData> lxsxPairs;

    @Override
    public int size(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (dir == EdgeDirection.OUTGOING) {
            if (e.getEvent() instanceof RMWStoreExclusive) {
                return lxsxPairs.containsKey(e) ? 1 : 0;
            }
        } else if (e.getEvent() instanceof Load) {
            return lxsxPairs.containsValue(e) ? 1 : 0;
        }
        return 0;
    }

    @Override
    public boolean contains(final EventData first, final EventData second) {
        return lxsxPairs.get(second) == first;
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        lxsxPairs = trace.getLxsxPairs();
        size = lxsxPairs.size();
    }

    @Override
    public void forwardPropagate(final Collection<EventData> added) {
        size = lxsxPairs.size();
    }

    @Override
    public List<EventData> inEvents(final EventData event) {
        if (!(event.getEvent() instanceof RMWStoreExclusive)) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(lxsxPairs.get(event));
        }
    }
    @Override
    public List<EventData> outEvents(final EventData event) {
        if (event.getReadFrom() == null) {
            return Collections.emptyList();
        }
        for (final Map.Entry<EventData, EventData> lxsxPair : lxsxPairs.entrySet()) {
            if (lxsxPair.getValue() == event) {
                return Collections.singletonList(lxsxPair.getKey());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Stream<Edge> edgeStream() {
        return lxsxPairs.entrySet().stream()
                .map(rmw -> new Edge(rmw.getValue().getId(), rmw.getKey().getId()));
    }

    @Override
    public Stream<Edge> edgeStream(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (dir == EdgeDirection.OUTGOING) {
            if (e.getEvent() instanceof RMWStoreExclusive) {
                final EventData load = lxsxPairs.get(e);
                if (load != null) {
                    return Stream.of(new Edge(load.getId(), id));
                }
            }
        } else if (e.getEvent() instanceof Load) {
            for (final Map.Entry<EventData, EventData> rmw : lxsxPairs.entrySet()) {
                final EventData load = rmw.getValue();
                if (load == e) {
                    return Stream.of(new Edge(load.getId(), rmw.getKey().getId()));
                }
            }
        }
        return Stream.empty();
    }
}
