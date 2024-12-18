package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;


import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.ArrayList;
import java.util.stream.Stream;

public class GenMCReadFromGraph extends StaticGenMCWMMGraph {

    @Override
    public boolean containsById(int id1, int id2) {
        EventData e = getEvent(id2).getReadFrom();
        return e != null && e.getId() == id1;
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
        size = trace.getReadWriteMap().size();
    }

    private Edge makeEdge(int a, int b) {
        return new Edge(a, b);
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
        } else if (e.isRead() && e.getReadFrom() != null) {
            return dir == EdgeDirection.INGOING ?
                    Stream.of(new Edge(e.getReadFrom().getId(), id)) : Stream.empty();
        } else {
            return Stream.empty();
        }
    }
}
