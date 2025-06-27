package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;


import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class GenMCReadFromGraph extends StaticGenMCWMMGraph {

    @Override
    public boolean contains(EventData first, EventData second) {
        return first == second.getReadFrom();
    }

    @Override
    public int size(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        if (dir == EdgeDirection.INGOING) {
            return e.getReadFrom() == null ? 0 : 1;
        } else  {
            return e.isWrite() ? trace.getWriteReadsMap().get(e).size() : 0;
        }
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        size = trace.getReadWriteMap().size();
    }

    private Edge makeEdge(int a, int b) {
        return new Edge(a, b);
    }

    @Override
    public List<EventData> inEvents(EventData event) {
        if (event.getReadFrom() != null) {
            return Collections.singletonList(event.getReadFrom());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<EventData> outEvents(EventData event) {
        if (event.isWrite()) {
            return trace.getWriteReadsMap().getOrDefault(event, new ArrayList<>());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Stream<Edge> edgeStream() {
        return trace.getReadWriteMap().entrySet().stream()
                .map(x -> new Edge(x.getValue().getId(), x.getKey().getId()));
    }

    @Override
    public Stream<Edge> edgeStream(int id, EdgeDirection dir) {
        EventData e = getEvent(id);
        if (e.isWrite()) {
            return dir == EdgeDirection.INGOING ? Stream.empty() :
                    trace.getWriteReadsMap().getOrDefault(e, new ArrayList<>()).stream().map(read -> makeEdge(id, read.getId()));
        } else if (e.getReadFrom() != null) {
            return dir == EdgeDirection.INGOING ?
                    Stream.of(new Edge(e.getReadFrom().getId(), id)) : Stream.empty();
        } else {
            return Stream.empty();
        }
    }
}
