package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;

import java.util.Collection;

// Used for static relations that are not yet implemented explicitly
public class StaticDefaultGenMCWMMGraph extends MaterializedGenMCWMMGraph {
    private final Relation relation;
    private final RelationAnalysis ra;

    public StaticDefaultGenMCWMMGraph(final Relation rel, final RelationAnalysis relationAnalysis) {
        this.relation = rel;
        this.ra = relationAnalysis;
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        super.repopulate();
        ra.getKnowledge(relation).getMaySet().apply((e1, e2) -> {
            final EventData d1 = trace.getData(e1).orElse(null);
            final EventData d2 = trace.getData(e2).orElse(null);
            if (d1 != null && d2 != null) {
                simpleGraph.addDisjoint(new Edge(d1.getId(), d2.getId()));
            }
        });
    }

    @Override
    public void forwardPropagate(final Collection<EventData> added) {
        throw new UnsupportedOperationException("Not supported.");
    }
}
