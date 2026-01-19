package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;


import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;
import com.google.common.collect.Iterators;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.*;

public class GenMCCoherenceGraph extends StaticGenMCWMMGraph {

    private List<EventData> coMap;

    @Override
    public int size(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (dir == EdgeDirection.INGOING) {
            if (e.getCoPredecessor() == null) {
                return 0;
            }
            return e.getCoPredecessors().size() - 1;
        } else {
            if (e.getCoSuccessor() == null) {
                return 0;
            }
            return e.getCoSuccessors().size() - 1;
        }
    }

    @Override
    public boolean containsById(final int id1, final int id2) {
        EventData iter = getEvent(id1).getCoSuccessor();
        while (iter != null) {
            if (iter.getId() == id2) {
                return true;
            }
            iter = iter.getCoSuccessor();
        }
        return false;
    }

    @Override
    public boolean contains(final EventData first, final EventData second) {
        EventData iter = first.getCoSuccessor();
        while (iter != null) {
            if (iter == second) {
                return true;
            }
            iter = iter.getCoSuccessor();
        }
        return false;
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        final Collection<EventData> addressInits = trace.getAddressInits().values();
        coMap = new ArrayList<>(addressInits.size());
        for (final EventData addressInit : addressInits) {
            if (addressInit.getCoSuccessor() != null) {
                coMap.add(addressInit);
            }
        }
        size = -1;
    }

    @Override
    public void forwardPropagate(final Collection<EventData> added) {
        for (final EventData addressInit : trace.getAddressInits().values()) {
            if (addressInit.getCoSuccessor() != null && !coMap.contains(addressInit)) {
                coMap.add(addressInit);
            }
        }
    }

    private void computeSize() {
        size = 0;
        for (final EventData addressInit : coMap) {
            final int sameAddressWrites = addressInit.getCoSuccessors().size();
            size += (sameAddressWrites * (sameAddressWrites - 1));
        }
        size >>= 1;
    }

    @Override
    public int size() {
        if (size == -1) {
            computeSize();
        }
        return size;
    }

    @Override
    public int getEstimatedSize() { return size(); }

    @Override
    public List<EventData> inEvents(final EventData event) {
        final EventData pre = event.getCoPredecessor();
        return pre == null ? Collections.emptyList() : pre.getCoPredecessors();
    }

    @Override
    public List<EventData> outEvents(final EventData event) {
        final EventData override = event.getCoSuccessor();
        return override == null ? Collections.emptyList() : override.getCoSuccessors();
    }

    @Override
    public Stream<Edge> edgeStream() {
        return StreamSupport.stream(
                Spliterators.spliterator(edgeIterator(), size(), SIZED | NONNULL | DISTINCT | IMMUTABLE | ORDERED),
                false
        );
    }

    @Override
    public Stream<Edge> edgeStream(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (!e.isWrite()) {
            return Stream.empty();
        }
        final Function<EventData, Edge> mapping = dir == EdgeDirection.INGOING ?
                (event -> new Edge(event.getId(), id)) : (event -> new Edge(id, event.getId()));
        return getCoSuccessorList(e, dir).stream().map(mapping);
    }

    @Override
    public Iterator<Edge> edgeIterator() {
        return new CoIterator();
    }

    @Override
    public Iterator<Edge> edgeIterator(final int id, final EdgeDirection dir) {
        final EventData e = getEvent(id);
        if (!e.isWrite()) {
            return Collections.emptyIterator();
        }
        final com.google.common.base.Function<EventData, Edge> mapping = dir == EdgeDirection.INGOING ?
                (event -> new Edge(event.getId(), id)) : (event -> new Edge(id, event.getId()));
        return Iterators.transform(getCoSuccessorList(e, dir).iterator(), mapping);
    }

    private List<EventData> getCoSuccessorList(final EventData e, final EdgeDirection dir) {
        final List<EventData> coSuccessorList = (dir == EdgeDirection.INGOING ?
                e.getCoPredecessors()
                : e.getCoSuccessors());
        coSuccessorList.remove(0);
        return coSuccessorList;
    }

    private class CoIterator implements Iterator<Edge> {

        private final Iterator<EventData> coListIterator = coMap.iterator();
        private List<EventData> curList = Collections.emptyList();
        private int low = 0, high = 0;
        private Edge edge;

        public CoIterator() {
            findNext();
        }

        private void findNext() {
            if (++high >= curList.size()) {
                if (++low < curList.size() - 1) {
                    high = low + 1;
                } else {
                    if (coListIterator.hasNext()) {
                        curList = coListIterator.next().getCoSuccessors();
                        low = 0;
                        high = 1;
                    } else {
                        edge = null;
                        return;
                    }
                }
            }
            edge = new Edge(curList.get(low).getId(), curList.get(high).getId());

        }

        @Override
        public boolean hasNext() {
            return edge != null;
        }

        @Override
        public Edge next() {
            final Edge e = edge;
            findNext();
            return e;
        }

    }
}
