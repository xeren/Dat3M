package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.event.core.RMWStore;
import com.dat3m.dartagnan.program.event.lang.svcomp.BeginAtomic;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GenMCAMOGraph extends MaterializedGenMCWMMGraph {

    public GenMCAMOGraph() { }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        super.repopulate();
        for (final List<EventData> events : trace.getThreadEventsMap().values()) {
            for (final EventData e : events) {
                if (e.getEvent() instanceof final RMWStore rmwStore) {
                    final EventData load = trace.getData(rmwStore.getLoadEvent()).get();
                    simpleGraph.addDisjoint(new Edge(load.getId(), e.getId()));
                } else if (e.getEvent() instanceof BeginAtomic) {
                    throw new UnsupportedOperationException("Atomic blocks for GenMC are not supported yet.");
                }
            }
        }
    }

    @Override
    public void forwardPropagate(final Collection<EventData> added) {
        for (final EventData e : added) {
            if (e.getEvent() instanceof final RMWStore rmwStore) {
                final EventData load = trace.getData(rmwStore.getLoadEvent()).get();
                simpleGraph.addDisjoint(new Edge(load.getId(), e.getId()));
            } else if (e.getEvent() instanceof BeginAtomic) {
                throw new UnsupportedOperationException("Atomic blocks for GenMC are not supported yet.");
            }
        }
    }

    @Override
    public List<EventData> inEvents(final EventData event) {
        if (!(event.getEvent() instanceof final RMWStore rmwStore)) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(trace.getData(rmwStore.getLoadEvent()).get());
        }
    }
    @Override
    public List<EventData> outEvents(final EventData event) {
        if (event.getReadFrom() == null) {
            return Collections.emptyList();
        }
        return super.outEvents(event);
    }
}
