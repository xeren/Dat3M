package com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.stat;

import com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.EventGraph;
import com.dat3m.dartagnan.analysis.graphRefinement.graphs.eventGraph.utils.MaterializedGraph;
import com.dat3m.dartagnan.analysis.graphRefinement.util.GraphVisitor;
import com.dat3m.dartagnan.verification.model.Edge;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.ExecutionModel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RMWGraph extends MaterializedGraph {

    public RMWGraph() { }

    @Override
    public List<? extends EventGraph> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    public void constructFromModel(ExecutionModel model) {
        super.constructFromModel(model);
        populate();
    }

    @Override
    public <TRet, TData, TContext> TRet accept(GraphVisitor<TRet, TData, TContext> visitor, TData data, TContext context) {
        return visitor.visitBase(this, data, context);
    }


    /* Right now there are four cases where the RMW-Relation is established:
     (1) LOCK : Load -> CondJump -> Store
     (2) RMW : RMWLoad -> RMWStore (completely static)
     (3) ExclAccess : ExclLoad -> ExclStore (dependent on control flow)
     (4) Atomic blocks: BeginAtomic -> Events* -> EndAtomic

     We need to keep this in line with the implementation of RelRMW!
    */
    private void populate() {
        // Atomic blocks
        model.getAtomicBlocksMap().values().stream().flatMap(Collection::stream).forEach(
                block -> {
                    for (int i = 0; i < block.size(); i++) {
                        for (int j = i + 1; j < block.size(); j++) {
                            simpleGraph.add(new Edge(block.get(i), block.get(j)));
                        }
                    }
                }
        );

        for (List<EventData> events : model.getThreadEventsMap().values()) {
            EventData lastExclLoad = null;
			for(EventData e : events) {
				if(!e.isRMW())
					continue;
				if(e.isRead()) {
					lastExclLoad = e;
				}
				else if(e.isWrite()) {
					if(lastExclLoad == null)
						throw new IllegalStateException("Exclusive store was executed without preceding exclusive load.");
					simpleGraph.add(new Edge(lastExclLoad, e));
					lastExclLoad = null;
				}
			}
        }
    }
}
