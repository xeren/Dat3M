package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.event.core.RMWStoreExclusive;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.*;

public class GenMCLXSXGraph extends MaterializedGenMCWMMGraph {

    private Map<EventData, EventData> lxsxPairs;

    public GenMCLXSXGraph() { }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        super.repopulate();
        lxsxPairs = trace.getLxsxPairs();
        for (final Map.Entry<EventData, EventData> lxsxPair : lxsxPairs.entrySet()) {
            simpleGraph.addDisjoint(new Edge(lxsxPair.getValue().getId(), lxsxPair.getKey().getId()));
        }
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
    public void forwardPropagate(final Collection<EventData> added) {
        for (final EventData event : added) {
            if (event.getEvent() instanceof RMWStoreExclusive) {
                simpleGraph.addDisjoint(new Edge(lxsxPairs.get(event).getId(), event.getId()));
            }
        }
    }
}
