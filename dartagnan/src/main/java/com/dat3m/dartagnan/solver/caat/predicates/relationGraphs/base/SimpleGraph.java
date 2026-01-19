package com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.base;

import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.misc.EdgeList;
import com.dat3m.dartagnan.solver.caat.predicates.CAATPredicate;
import com.dat3m.dartagnan.solver.caat.predicates.Derivable;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.RelationGraph;

import java.util.*;
import java.util.stream.Stream;


/*
    This is a simple graph that allows adding edges directly.
    It is mostly used as an internal implementation for many relationgraphs.
 */
public final class SimpleGraph extends AbstractBaseGraph {
    private DataItem[] outgoing = new DataItem[0];
    private DataItem[] ingoing = new DataItem[0];
    private int maxTime = 0;
    private int numEvents = 0;
    private int size = 0;

    @Override
    public List<RelationGraph> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<Edge> forwardPropagate(final CAATPredicate changedSource, final Collection<? extends Derivable> added) {
        final List<Edge> changes = new ArrayList<>( added.size());
        for (final Edge e : (Collection<Edge>)added) {
            if (add(e)) {
                changes.add(e);
            }
        }
        return changes;
    }

    @Override
    public void backtrackTo(final int time) {
        if (maxTime <= time) {
            return;
        }
        final int bound = Math.min(numEvents, outgoing.length);
        for (int i = 0; i < bound; i++) {
            final DataItem item = outgoing[i];
            if (item != null) {
                item.backtrackTo(time);
                maxTime = Math.max(maxTime, item.maxTime);
            }
        }

        final int bound2 = Math.min(numEvents, ingoing.length);
        for (int i = 0; i < bound2; i++) {
            final DataItem item = ingoing[i];
            if (item != null) {
                item.backtrackTo(time);
            }
        }

    }

    private DataItem getItem(final int e, final EdgeDirection dir) {
        switch (dir) {
            case OUTGOING:
                return outgoing[e];
            case INGOING:
                return ingoing[e];
            default:
                return null;
        }
    }

    public Collection<Edge> getEdges(final int e, final EdgeDirection dir) {
        final DataItem item = getItem(e, dir);
        return item == null ? Collections.emptyList() : item.edgeList;
    }

    public Edge get(final Edge edge) {
        final DataItem item1 = outgoing[edge.getFirst()];
        final DataItem item2 = ingoing[edge.getSecond()];
        if (item1 != null && item2 != null) {
            final DataItem parallelEdges = item1.size() <= item2.size() ? item1 : item2;
            for (final Edge e : parallelEdges.edgeList) {
                if (e.equals(edge)) {
                    return e;
                }
            }
            return null;
        }
        return null;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int size(final int e, final EdgeDirection dir) {
        final DataItem item = getItem(e, dir);
        return item == null ? 0 : item.size();
    }

    public boolean contains(final Edge e) {
        return contains(e, outgoing[e.getFirst()], ingoing[e.getSecond()]);
    }

    private boolean contains(final Edge e, final DataItem outgoing, final DataItem ingoing) {
        if (outgoing != null && ingoing != null) {
            if (outgoing.size() <= ingoing.size()) {
                return outgoing.contains(e);
            } else {
                return ingoing.contains(e);
            }
        }
        return false;
    }

    public boolean add(final Edge e) {
        return add(e, false);
    }

    public void addDisjoint(final Edge e) {
        add(e, true);
    }

    public boolean add(final Edge e, final boolean disjoint) {
        final int firstId = e.getFirst();
        final int secondId = e.getSecond();
        DataItem item1 = outgoing[firstId];
        DataItem item2 = ingoing[secondId];
        if (!disjoint && contains(e, item1, item2)) {
            return false;
        }
        ++size;
        maxTime = Math.max(maxTime, e.getTime());

        if (item1 == null) {
            outgoing[firstId] = item1 = new DataItem(true, false);
        }
        item1.add(e);

        if (item2 == null) {
            ingoing[secondId] = item2 = new DataItem( false, true);
        }
        item2.add(e);

        return true;
    }

    public boolean addAll(final Collection<? extends Edge> c) {
        boolean changed = false;
        for (Edge e : c) {
            changed |= add(e);
        }
        return changed;
    }

    public void clear() {
        maxTime = 0;
        size = 0;

        for (final DataItem item : outgoing) {
            if (item != null) {
                item.clear();
            }
        }

        for (final DataItem item : ingoing) {
            if (item != null) {
                item.clear();
            }
        }
    }

    @Override
    public Stream<Edge> edgeStream() {
        return Arrays.stream(outgoing, 0, Math.min(numEvents, outgoing.length))
                .filter(item -> item != null && !item.isEmpty())
                .flatMap(DataItem::stream);
    }

    @Override
    public Stream<Edge> edgeStream(final int e, final EdgeDirection dir) {
        final DataItem item = getItem(e, dir);
        return item == null ? Stream.empty() : item.stream();
    }

    @Override
    public Iterator<Edge> edgeIterator(final int e, final EdgeDirection dir) {
        final DataItem item = getItem(e, dir);
        return item == null ? Collections.emptyIterator() : item.iterator();
    }

    @Override
    public Iterator<Edge> edgeIterator() {
        return new EdgeIterator();
    }

    @Override
    public void initializeToDomain(final Domain<?> domain) {
        super.initializeToDomain(domain);

        clear();
        numEvents = domain.size();
        if (numEvents > outgoing.length) {
            final int newCapacity = numEvents + 20; // We give a buffer of 20 extra events
            outgoing = Arrays.copyOf(outgoing, newCapacity);
            ingoing = Arrays.copyOf(ingoing, newCapacity);
        }
    }

    public void updateDomain(final Domain<?> domain) {
        numEvents = domain.size();
        if (numEvents > outgoing.length) {
            final int newCapacity = numEvents + 20; // We give a buffer of 20 extra events
            outgoing = Arrays.copyOf(outgoing, newCapacity);
            ingoing = Arrays.copyOf(ingoing, newCapacity);
        }
    }

    @Override
    public String toString() {
        return name != null ? name : SimpleGraph.class.getSimpleName() + ": " + size();
    }

    private final class DataItem implements Iterable<Edge> {
        final List<Edge> edgeList;
        final boolean isOutgoing;
        final boolean isIngoing;
        int maxTime;
        int size = 0;

        public DataItem(final boolean isOutgoing, final boolean isIngoing) {
            edgeList = new EdgeList(20);
            this.isOutgoing = isOutgoing;
            this.isIngoing = isIngoing;
            maxTime = 0;
        }

        public int size() {
            return size;
        }
        public boolean isEmpty() {
            return size == 0;
        }

        public boolean add(final Edge e) {
            edgeList.add(e);
            maxTime = Math.max(maxTime, e.getTime());
            ++size;
            return true;
        }

        public boolean contains(final Edge e) {
            if (isOutgoing) {
                final int second = e.getSecond();
                for (final Edge item : edgeList) {
                    if (item.getSecond() == second) {
                        return true;
                    }
                }
            } else if (isIngoing) {
                final int first = e.getFirst();
                for (final Edge item : edgeList) {
                    if (item.getFirst() == first) {
                        return true;
                    }
                }
            } else {
                return edgeList.contains(e);
            }
            return false;
        }

        public Iterator<Edge> iterator() {
            return edgeList.iterator();
        }
        public Stream<Edge> stream() {
            return edgeList.stream();
        }

        public void clear() {
            edgeList.clear();
            maxTime = 0;
            size = 0;
        }

        public void backtrackTo(final int time) {
            //NOTE: We use the fact that the edge list
            // should be sorted by timestamp (since edges with higher timestamp get added later)
            if (maxTime > time) {
                final List<Edge> edgeList = this.edgeList;
                int i = edgeList.size();
                while (--i >= 0) {
                    final Edge e = edgeList.get(i);
                    if (e.getTime() > time) {
                        edgeList.remove(i);
                        if (isOutgoing) {
                            --SimpleGraph.this.size;
                        }
                        --size;
                    } else {
                        maxTime = e.getTime();
                        return;
                    }
                }
                maxTime = 0;
            }
        }

    }

    private class EdgeIterator implements Iterator<Edge> {

        int index = -1;
        List<Edge> innerList = Collections.emptyList();
        int innerIndex = 0;
        Edge edge = null;

        public EdgeIterator() {
            findNext();
        }

        private void findNext() {
            edge = null;
            if (++innerIndex >= innerList.size()) {
                innerIndex = 0;
                while (++index < numEvents) {
                    final DataItem item = outgoing[index];
                    if (item != null && !item.isEmpty()) {
                        innerList = item.edgeList;
                        edge = innerList.get(0);
                        return;
                    }
                }
            } else {
                edge = innerList.get(innerIndex);
            }
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
