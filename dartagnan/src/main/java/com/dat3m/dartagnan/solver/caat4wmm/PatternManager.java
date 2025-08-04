package com.dat3m.dartagnan.solver.caat4wmm;

import com.dat3m.dartagnan.solver.caat.predicates.Derivable;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.Edge;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.RelationGraph;
import com.dat3m.dartagnan.solver.caat.predicates.sets.Element;
import com.dat3m.dartagnan.solver.caat.reasoning.CAATLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc.AbstractGenMCWMMGraph;
import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc.StaticGenMCWMMSet;
import com.dat3m.dartagnan.utils.logic.Conjunction;
import com.dat3m.dartagnan.utils.logic.DNF;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.wmm.Relation;

import java.util.*;

public class PatternManager implements Extractor {

    private static final boolean USE_SETS = true;

    private final Map<RelationGraph, Relation> graphs;
    private final List<ViolationPattern> patterns = new ArrayList<>();

    public PatternManager(GenMCExecutionGraph graph) {
        this.graphs = graph.getRelationGraphMap().inverse();
    }

    @Override
    public void extract(DNF<CAATLiteral> inconsistencyReasons) {
        List<ViolationPattern> newPatterns = new ArrayList<>();
        REASON_LOOP:
        for (Conjunction<CAATLiteral> reason : inconsistencyReasons.getCubes()) {
            ViolationPattern pattern = new ViolationPattern();
            Map<Integer, ViolationPattern.Node> nodes = new HashMap<>();
            List<Map.Entry<Edge, AbstractGenMCWMMGraph>> edges = new ArrayList<>(reason.getSize());
            for (CAATLiteral literal : reason.getLiterals()) {
                Derivable derivable = literal.getData();
                if (USE_SETS && derivable instanceof Element condition) {
                    int id = condition.getId();
                    ViolationPattern.Node node = nodes.get(id);
                    if (node == null) {
                        node = pattern.addNode(new ArrayList<>(), new ArrayList<>());
                        nodes.put(id, node);
                    }
                    (literal.isPositive() ? node.positiveConditions() : node.negativeConditions()).add((StaticGenMCWMMSet) literal.getPredicate());
                } else if (literal.isPositive() && derivable instanceof Edge edge) {
                    edges.add(Map.entry(edge, (AbstractGenMCWMMGraph) literal.getPredicate()));
                } else continue REASON_LOOP;
            }
            for (Map.Entry<Edge, AbstractGenMCWMMGraph> literal : edges) {
                Edge edge = literal.getKey();
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
                AbstractGenMCWMMGraph graph = literal.getValue();
                Relation rel = graphs.get(graph);
                pattern.addRelationGraph(rel, graph);
                pattern.addEdge(rel, firstNode, secondNode, false);
            }
            if (!existsPattern(newPatterns, pattern)) {
                newPatterns.add(pattern);
            }
        }
        patterns.addAll(newPatterns);
    }

    private boolean existsPattern(List<ViolationPattern> patterns, ViolationPattern pattern) {
        PATTERN_LOOP:
        for (ViolationPattern otherPattern : patterns) {
            if (pattern.edges.size() == otherPattern.edges.size()) {
                for (int i = 0; i < pattern.edges.size(); ++i) {
                    ViolationPattern.Edge thisEdge = pattern.edges.get(i);
                    ViolationPattern.Edge otherEdge = otherPattern.edges.get(i);
                    if (thisEdge.relation() != otherEdge.relation() || thisEdge.isNegated() != otherEdge.isNegated()
                            || thisEdge.from().id() != otherEdge.from().id() || thisEdge.to().id() != otherEdge.to().id()) {
                        continue PATTERN_LOOP;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public EventData isViolated(EventData first, EventData second, Relation relation) {
        for (ViolationPattern pattern : patterns) {
            for (ViolationPattern.Edge patternEdge : pattern.findEdgesByRelation(relation)) {
                List<ViolationPattern.Match> matches;
                if (!(matches = pattern.findMatches(patternEdge, first, second)).isEmpty()) {
                    EventData backtrackingPoint = null;
                    for (ViolationPattern.Match match : matches) {
                        EventData[] matchEvents = match.toArray();
                        EventData cycleEvent = null;
                        for (EventData event : matchEvents) {
                            if (cycleEvent == null || event.getId() > cycleEvent.getId()) {
                                cycleEvent = event;
                            }
                        }
                        if (backtrackingPoint == null || backtrackingPoint.getId() > cycleEvent.getId()) {
                            backtrackingPoint = cycleEvent;
                        }
                    }
                    return backtrackingPoint;
                }
            }
        }
        return null;
    }
}
