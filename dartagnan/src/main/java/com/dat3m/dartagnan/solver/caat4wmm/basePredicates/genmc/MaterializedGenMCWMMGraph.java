package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.base.SimpleGraph;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/*
    As opposed to a "virtualized graph", a materialized graph actually stores its nodes/edges explicitly.
    MaterializedGraph simply encapsulates a SimpleGraph and delegates all its methods
    to the underlying SimpleGraph (similar to a forwarding decorator).
 */
public abstract class MaterializedGenMCWMMGraph extends AbstractGenMCWMMGraph {

    protected final SimpleGraph simpleGraph;

    protected MaterializedGenMCWMMGraph() {
        this.simpleGraph = new SimpleGraph();
    }

    @Override
    public Edge get(final Edge edge) {
        return simpleGraph.get(edge);
    }

    @Override
    public boolean containsById(final int a, final int b) {
        return simpleGraph.containsById(a, b);
    }

    @Override
    public boolean contains(final Edge edge) {
        return simpleGraph.contains(edge);
    }

    @Override
    public void backtrackTo(final int time) {
        simpleGraph.backtrackTo(time);
    }

    @Override
    public void initializeToDomain(final Domain<?> domain) {
        if (isDomainInitForbidden()) return;
        super.initializeToDomain(domain);
        simpleGraph.initializeToDomain(domain);
    }

    @Override
    public int getEstimatedSize() {
        return simpleGraph.getEstimatedSize();
    }

    @Override
    public int getEstimatedSize(final int e, final EdgeDirection dir) {
        return simpleGraph.getEstimatedSize(e, dir);
    }

    @Override
    public int getMinSize() {
        return simpleGraph.getMinSize();
    }

    @Override
    public int getMinSize(final int e, final EdgeDirection dir) {
        return simpleGraph.getMinSize(e, dir);
    }

    @Override
    public int getMaxSize() {
        return simpleGraph.getMaxSize();
    }

    @Override
    public int getMaxSize(final int e, final EdgeDirection dir) {
        return simpleGraph.getMaxSize(e, dir);
    }

    @Override
    public Stream<Edge> edgeStream() {
        return simpleGraph.edgeStream();
    }

    @Override
    public Stream<Edge> edgeStream(final int e, final EdgeDirection dir) {
        return simpleGraph.edgeStream(e, dir);
    }

    @Override
    public Iterator<Edge> edgeIterator() {
        return simpleGraph.edgeIterator();
    }

    @Override
    public Iterator<Edge> edgeIterator(final int e, final EdgeDirection dir) {
        return simpleGraph.edgeIterator(e, dir);
    }

    @Override
    public int size() {
        return simpleGraph.size();
    }

    @Override
    public int size(final int e, final EdgeDirection dir) { return simpleGraph.size(e, dir); }

    @Override
    public boolean isEmpty() {
        return simpleGraph.isEmpty();
    }

    @Override
    public List<EventData> inEvents(final EventData event) {
        final List<EventData> inEvents = new ArrayList<>();
        for (final Edge edge : simpleGraph.getEdges(event.getId(), EdgeDirection.INGOING)) {
            inEvents.add(getEvent(edge.getFirst()));
        }
        return inEvents;
    }
    @Override
    public List<EventData> outEvents(final EventData event) {
        final List<EventData> outEvents = new ArrayList<>();
        for (final Edge edge : simpleGraph.getEdges(event.getId(), EdgeDirection.OUTGOING)) {
            outEvents.add(getEvent(edge.getSecond()));
        }
        return outEvents;
    }

}
