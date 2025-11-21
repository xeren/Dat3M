package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.event.core.RMWStoreExclusive;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GenMCLXSXGraph extends MaterializedGenMCWMMGraph {

    private Map<EventData, EventData> lxsxPairs;

    public GenMCLXSXGraph() { }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        lxsxPairs = trace.getLxsxPairs();
        for (Map.Entry<EventData, EventData> lxsxPair : lxsxPairs.entrySet()) {
            simpleGraph.addDisjoint(new Edge(lxsxPair.getValue().getId(), lxsxPair.getKey().getId()));
        }
    }

    @Override
    public List<EventData> inEvents(EventData event) {
        if (!(event.getEvent() instanceof RMWStoreExclusive)) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(lxsxPairs.get(event));
        }
    }
    @Override
    public List<EventData> outEvents(EventData event) {
        if (event.getReadFrom() == null) {
            return Collections.emptyList();
        }
        for (Map.Entry<EventData, EventData> lxsxPair : lxsxPairs.entrySet()) {
            if (lxsxPair.getValue() == event) {
                return Collections.singletonList(lxsxPair.getKey());
            }
        }
        return Collections.emptyList();
    }
}
