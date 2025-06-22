package com.dat3m.dartagnan.solver.caat4wmm;

import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.RelationGraph;
import com.dat3m.dartagnan.solver.caat.reasoning.CAATLiteral;
import com.dat3m.dartagnan.utils.logic.Conjunction;
import com.dat3m.dartagnan.utils.logic.DNF;
import com.dat3m.dartagnan.wmm.Relation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatternManager implements Extractor {

    private final GenMCExecutionGraph graphs;
    private final List<ViolationPattern> patterns = new ArrayList<>();

    public PatternManager(GenMCExecutionGraph graph) {
        this.graphs = graph;
    }

    @Override
    public void extract(DNF<CAATLiteral> inconsistencyReasons) {
        REASON_LOOP:
        for (Conjunction<CAATLiteral> reason : inconsistencyReasons.getCubes()) {
            ViolationPattern pattern = new ViolationPattern();
            Map<Integer, ViolationPattern.Node> nodes = new HashMap<>();
            for (CAATLiteral literal : reason.getLiterals()) {
                if (literal.isPositive() && literal.getData() instanceof Edge edge) {
                    int firstId = edge.getFirst();
                    int secondId = edge.getSecond();
                    ViolationPattern.Node firstNode = nodes.get(firstId);
                    ViolationPattern.Node secondNode = firstId == secondId ? firstNode : nodes.get(secondId);
                    if (firstNode == null) {
                        firstNode = pattern.addNode();
                        nodes.put(firstId, firstNode);
                        if (firstId == secondId) {
                            secondNode = firstNode;
                        }
                    }
                    if (secondNode == null) {
                        secondNode = pattern.addNode();
                        nodes.put(secondId, secondNode);
                    }
                    RelationGraph graph = (RelationGraph) literal.getPredicate();
                    Relation rel = graphs.getRelationGraphMap().inverse().get(graph);
                    pattern.addRelationGraph(rel, graph);
                    pattern.addEdge(rel, firstNode, secondNode, false);
                } else continue REASON_LOOP;
            }
            patterns.add(pattern);
        }
    }

    public boolean isViolated(Edge edge, Relation relation) {
        for (ViolationPattern pattern : patterns) {
            for (ViolationPattern.Edge patternEdge : pattern.findEdgesByRelation(relation)) {
                if (!pattern.findMatches(patternEdge, edge.getFirst(), edge.getSecond()).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
