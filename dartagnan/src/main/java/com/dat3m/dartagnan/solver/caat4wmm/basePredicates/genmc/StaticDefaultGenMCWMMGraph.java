package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;

// Used for static relations that are not yet implemented explicitly
public class StaticDefaultGenMCWMMGraph extends MaterializedGenMCWMMGraph {
    private final Relation relation;
    private final RelationAnalysis ra;

    public StaticDefaultGenMCWMMGraph(Relation rel, RelationAnalysis relationAnalysis) {
        this.relation = rel;
        this.ra = relationAnalysis;
    }

    @Override
    public void repopulate() {
        ra.getKnowledge(relation).getMaySet().apply((e1, e2) -> {
            EventData d1 = trace.getData(e1).orElse(null);
            EventData d2 = trace.getData(e2).orElse(null);
            if (d1 != null && d2 != null) {
                simpleGraph.add(new Edge(d1.getId(), d2.getId()));
            }
        });
    }
}
