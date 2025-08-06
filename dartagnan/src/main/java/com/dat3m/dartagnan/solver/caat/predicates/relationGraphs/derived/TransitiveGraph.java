package com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.derived;

import com.dat3m.dartagnan.solver.caat.predicates.CAATPredicate;
import com.dat3m.dartagnan.solver.caat.predicates.Derivable;
import com.dat3m.dartagnan.solver.caat.predicates.misc.PredicateVisitor;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.MaterializedGraph;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.RelationGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TransitiveGraph extends MaterializedGraph {

    private final RelationGraph inner;

    @Override
    public List<RelationGraph> getDependencies() {
        return Collections.singletonList(inner);
    }

    public TransitiveGraph(RelationGraph inner) {
        this.inner = inner;
    }

    private Edge derive(Edge e) {
        return e.withDerivationLength(e.getDerivationLength() + 1);
    }

    private Edge combine(Edge a, Edge b, int time) {
        return new Edge(a.getFirst(), b.getSecond(), time,
                Math.max(a.getDerivationLength(), b.getDerivationLength()) + 1);
    }

    @Override
    public void repopulate() {
        boolean[][] adjacencyMatrix = new boolean[domain.size()][domain.size()];
        for (Edge edge : inner.edges()) {
            edge = derive(edge);
            int firstId = edge.getFirst();
            int secondId = edge.getSecond();
            if (!adjacencyMatrix[firstId][secondId]) {
                simpleGraph.addDisjoint(edge);
                adjacencyMatrix[firstId][secondId] = true;
                final int time = edge.getTime();

                for (Edge inEdge : inEdges(firstId)) {
                    Edge newEdge = combine(inEdge, edge, time);
                    int newFirst = newEdge.getFirst();
                    int newSecond = newEdge.getSecond();
                    if (!adjacencyMatrix[newFirst][newSecond]) {
                        simpleGraph.addDisjoint(newEdge);
                        adjacencyMatrix[newFirst][newSecond] = true;
                        for (Edge outEdge : outEdges(secondId)) {
                            Edge combined = combine(newEdge, outEdge, time);
                            int combinedFirst = combined.getFirst();
                            int combinedSecond = combined.getSecond();
                            if (!adjacencyMatrix[combinedFirst][combinedSecond]) {
                                simpleGraph.addDisjoint(combined);
                                adjacencyMatrix[combinedFirst][combinedSecond] = true;
                            }
                        }
                    }
                }

                for (Edge outEdge : outEdges(secondId)) {
                    Edge newEdge = combine(edge, outEdge, time);
                    int newFirst = newEdge.getFirst();
                    int newSecond = newEdge.getSecond();
                    if (!adjacencyMatrix[newFirst][newSecond]) {
                        simpleGraph.addDisjoint(newEdge);
                        adjacencyMatrix[newFirst][newSecond] = true;
                    }
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<Edge> forwardPropagate(CAATPredicate changedSource, Collection<? extends Derivable> added) {
        if (changedSource == inner) {
            List<Edge> newEdges = new ArrayList<>();
            for (Edge e : (Collection<Edge>)added) {
                updateEdge(derive(e), newEdges);
            }
            return newEdges;
        } else {
            return Collections.emptyList();
        }
    }

    // Every (transitive) edge that gets added by adding <edge> is collected into <addedEdges>
    private void updateEdge(Edge edge, Collection<Edge> addedEdges) {
        if (!simpleGraph.add(edge)) {
            return;
        }
        addedEdges.add(edge);
        final int time = edge.getTime();

        for (Edge inEdge : inEdges(edge.getFirst())) {
            Edge newEdge = combine(inEdge, edge, time);
            if (simpleGraph.add(newEdge)) {
                addedEdges.add(newEdge);
                for (Edge outEdge : outEdges(edge.getSecond())) {
                    Edge combined = combine(newEdge, outEdge, time);
                    if (simpleGraph.add(combined)) {
                        addedEdges.add(combined);
                    }
                }
            }
        }

        for (Edge outEdge : outEdges(edge.getSecond())) {
            Edge newEdge = combine(edge, outEdge, time);
            if (simpleGraph.add(newEdge)) {
                addedEdges.add(newEdge);
            }
        }
    }

    @Override
    public <TRet, TData, TContext> TRet accept(PredicateVisitor<TRet, TData, TContext> visitor, TData data, TContext context) {
        return visitor.visitTransitiveClosure(this, data, context);
    }

}
