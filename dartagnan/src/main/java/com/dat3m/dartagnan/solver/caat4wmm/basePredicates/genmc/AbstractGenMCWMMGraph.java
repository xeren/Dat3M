package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.predicates.CAATPredicate;
import com.dat3m.dartagnan.solver.caat.predicates.Derivable;
import com.dat3m.dartagnan.solver.caat.predicates.misc.PredicateVisitor;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.RelationGraph;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractGenMCWMMGraph extends AbstractGenMCWMMPredicate implements RelationGraph {

    @Override
    public List<RelationGraph> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    public <TRet, TData, TContext> TRet accept(PredicateVisitor<TRet, TData, TContext> visitor, TData tData, TContext context) {
        return visitor.visitBaseGraph(this, tData, context);
    }

    @Override
    public Collection<Edge> forwardPropagate(CAATPredicate changedSource, Collection<? extends Derivable> added) {
        return Collections.emptyList();
    }

    public boolean contains(EventData first, EventData second) {
        return containsById(first.getId(), second.getId());
    }

    public List<EventData> inEvents(EventData event) {
        return inEdgeStream(event.getId())
                .map(Edge::getFirst)
                .map(this::getEvent)
                .toList();
    }
    public List<EventData> outEvents(EventData event) {
        return outEdgeStream(event.getId())
                .map(Edge::getSecond)
                .map(this::getEvent)
                .toList();
    }
}
