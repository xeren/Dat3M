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
    public <TRet, TData, TContext> TRet accept(final PredicateVisitor<TRet, TData, TContext> visitor, TData tData, TContext context) {
        return visitor.visitBaseGraph(this, tData, context);
    }

    @Override
    public Collection<Edge> forwardPropagate(final CAATPredicate changedSource, final Collection<? extends Derivable> added) {
        return Collections.emptyList();
    }

    public boolean contains(final EventData first, final EventData second) {
        return containsById(first.getId(), second.getId());
    }

    public List<EventData> inEvents(final EventData event) {
        return inEdgeStream(event.getId())
                .map(Edge::getFirst)
                .map(this::getEvent)
                .toList();
    }
    public List<EventData> outEvents(final EventData event) {
        return outEdgeStream(event.getId())
                .map(Edge::getSecond)
                .map(this::getEvent)
                .toList();
    }
}
