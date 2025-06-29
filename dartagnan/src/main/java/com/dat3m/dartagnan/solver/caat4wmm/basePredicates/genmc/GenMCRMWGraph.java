package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.event.core.RMWStore;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.Collections;
import java.util.List;

public class GenMCRMWGraph extends MaterializedGenMCWMMGraph {

    public GenMCRMWGraph() { }

    /* Right now there are four cases where the RMW-Relation is established:
     (1) LOCK : Load -> CondJump -> Store
     (2) RMW : RMWLoad -> RMWStore (completely static)
     (3) ExclAccess : ExclLoad -> ExclStore (dependent on control flow)

     We need to keep this in line with the implementation of RelRMW!
    */
    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        for (List<EventData> events : trace.getThreadEventsMap().values()) {
            EventData lastExclLoad = null;
            for (EventData e : events) {
                if (e.getReadFrom() != null) {
                    if (e.isExclusive()) {  // LoadExcl
                        lastExclLoad = e;
                    }
                } else if (e.isWrite()) {
                    if (e.isExclusive()) { // StoreExcl
                        if (lastExclLoad == null) {
                            throw new IllegalStateException("Exclusive store was executed without preceding exclusive load.");
                        }
                        simpleGraph.add(new Edge(lastExclLoad.getId(), e.getId()));
                        lastExclLoad = null;
                    } else if (e.getEvent() instanceof RMWStore rmwStore) { // RMWStore
                        EventData load = trace.getData(rmwStore.getLoadEvent()).get();
                        simpleGraph.add(new Edge(load.getId(), e.getId()));
                    }
                }
            }
        }
    }

    @Override
    public List<EventData> inEvents(EventData event) {
        if (!(event.getEvent() instanceof RMWStore rmwStore)) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(trace.getData(rmwStore.getLoadEvent()).get());
        }
    }
    @Override
    public List<EventData> outEvents(EventData event) {
        if (event.getReadFrom() == null) {
            return Collections.emptyList();
        }
        return super.outEvents(event);
    }
}
