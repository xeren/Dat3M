package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.solver.caat.misc.EdgeDirection;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;


public abstract class StaticGenMCWMMGraph extends AbstractGenMCWMMGraph {
    protected int size;

    @Override
    public Edge get(Edge edge) {
        return contains(edge) ? edge.with(0, 0) : null;
    }

    @Override
    public boolean contains(Edge edge) {
        return containsById(edge.getFirst(), edge.getSecond());
    }

    @Override
    public boolean containsById(int id1, int id2) {
        return contains(getEvent(id1), getEvent(id2));
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int getEstimatedSize() { return size; }

    @Override
    public void backtrackTo(int time) { }

    @Override
    public void initializeToDomain(Domain<?> domain) {
        if (isDomainInitForbidden()) return;
        super.initializeToDomain(domain);
        size = 0;
    }

    protected final void autoComputeSize() {
        size = 0;
        for (int i = 0; i < domain.size(); i++) {
            size += size(i, EdgeDirection.OUTGOING);
        }
    }

}
