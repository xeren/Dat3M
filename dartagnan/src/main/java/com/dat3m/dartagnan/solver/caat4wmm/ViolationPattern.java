package com.dat3m.dartagnan.solver.caat4wmm;

import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc.AbstractGenMCWMMGraph;
import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc.StaticGenMCWMMSet;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.wmm.Relation;

import java.util.*;
import java.util.stream.Collectors;

public class ViolationPattern {

    final Map<Relation, AbstractGenMCWMMGraph> rel2Graph = new HashMap<>();
    final List<Node> nodes = new ArrayList<>();
    final List<Edge> edges = new ArrayList<>();

    // ------------------------------------------------------------------------------------------
    // Construction

    public Node addNode() {
        return addNode(Collections.emptyList(), Collections.emptyList());
    }

    public Node addNode(List<StaticGenMCWMMSet> positiveConditions,  List<StaticGenMCWMMSet> negativeConditions) {
        final Node node = new Node(nodes.size(), positiveConditions, negativeConditions);
        nodes.add(node);
        return node;
    }

    public Edge addEdge(Relation relation, Node from, Node to, boolean isStatic) {
        final AbstractGenMCWMMGraph graph = rel2Graph.get(relation);
        assert graph != null;
        final Edge edge = new Edge(relation, graph, from, to, false, isStatic);
        this.edges.add(edge);
        return edge;
    }

    public void addRelationGraph(Relation rel, AbstractGenMCWMMGraph graph) {
        rel2Graph.put(rel, graph);
    }

    // ------------------------------------------------------------------------------------------
    // Matching

    public List<Edge> findEdgesByRelation(Relation relation) {
        List<Edge> matches = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.relation == relation) {
                matches.add(edge);
            }
        }
        return matches;
    }

    public List<Match> findMatches(Edge edge, EventData from, EventData to) {
        if (!edge.from.check(from) || !edge.to.check(to)) {
            return Collections.emptyList();
        }

        final NodeSet visited = new NodeSet();
        visited.add(edge.from);
        visited.add(edge.to);

        final Match firstMatch = new Match(this);
        firstMatch.setMatch(edge.from, from);
        firstMatch.setMatch(edge.to, to);

        List<Match> curMatches = new ArrayList<>();
        curMatches.add(firstMatch);

        final EdgeQueue queue = new EdgeQueue(this);
        queue.remove(edge);

        Edge nextEdgeToJoin;
        while ((nextEdgeToJoin = queue.pop(visited)) != null) {
            final boolean joinAtSource = visited.add(nextEdgeToJoin.to);
            final boolean joinAtTarget = visited.add(nextEdgeToJoin.from);
            final boolean doPrune = !(joinAtSource || joinAtTarget);

            if (doPrune) {
                prune(curMatches, nextEdgeToJoin);
            } else {
                final Node matchedNode = joinAtSource ? nextEdgeToJoin.from : nextEdgeToJoin.to;
                final Node unmatchedNode = joinAtSource ? nextEdgeToJoin.to : nextEdgeToJoin.from;
                List<Match> nextMatches = new ArrayList<>();
                for (Match match : curMatches) {
                    final List<EventData> joinedNodeIds = joinAlong(match, matchedNode, nextEdgeToJoin);
                    for (EventData nodeId : joinedNodeIds) {
                        if (unmatchedNode.check(nodeId)) {
                            nextMatches.add(match.with(unmatchedNode, nodeId));
                        }
                    }
                }
                curMatches = nextMatches;
            }

            if (curMatches.isEmpty()) {
                return curMatches;
            }
        }


        return curMatches;
    }

    private List<EventData> joinAlong(Match partialMatch, Node node, Edge edge) {
        final boolean outgoing = edge.from == node;
        final Node target = outgoing ? edge.to : edge.from;
        final EventData nodeMatch = partialMatch.atNode(node);

        assert nodeMatch != null;
        assert partialMatch.atNode(target) == null;

        final AbstractGenMCWMMGraph relationGraph = edge.graph;
        return outgoing ? relationGraph.outEvents(nodeMatch) : relationGraph.inEvents(nodeMatch);
    }

    private void prune(List<Match> matches, Edge edge) {
        final AbstractGenMCWMMGraph relationGraph = edge.graph;
        matches.removeIf(m -> {
            final EventData source = m.atNode(edge.from);
            final EventData target = m.atNode(edge.to);
            return !relationGraph.contains(source, target);
        });
    }

    // ===============================================================================================
    // ================================ Internal classes =============================================
    // ===============================================================================================

    private static class NodeSet {
        private int nodeSet = 0;

        private static int toIndex(Node node) {
            return 1 << node.id;
        }

        public boolean contains(Node node) {
            return (nodeSet & toIndex(node)) != 0;
        }

        public boolean add(Node node) {
            final int old = nodeSet;
            nodeSet |= toIndex(node);
            return old != nodeSet;
        }
    }

    private static class EdgeQueue {
        private final List<Edge> unmatchedEdges;

        public EdgeQueue(ViolationPattern pattern) {
            unmatchedEdges = new ArrayList<>(pattern.edges);
        }

        public void remove(Edge edge) {
            unmatchedEdges.remove(edge);
        }

        public Edge pop(NodeSet visited) {
            if (unmatchedEdges.isEmpty()) {
                return null;
            }

            Edge candidate = null;
            for (Edge e : unmatchedEdges) {
                final boolean fromVisited = visited.contains(e.from);
                final boolean toVisited = visited.contains(e.to);
                if (fromVisited && toVisited) {
                    candidate = e;
                    break;
                } else if (candidate == null && !e.isStatic && (fromVisited || toVisited)) {
                    candidate = e;
                }
            }

            assert candidate != null;
            remove(candidate);
            return candidate;
        }
    }

    public record Node(int id, List<StaticGenMCWMMSet> positiveConditions, List<StaticGenMCWMMSet> negativeConditions) {
        @Override
        public String toString() { return "n#" + id; }

        @Override
        public boolean equals(Object obj) { return this == obj; }

        private boolean check(EventData event) {
            for (StaticGenMCWMMSet condition : positiveConditions) {
                if (!condition.contains(event)) {
                    return false;
                }
            }
            for (StaticGenMCWMMSet condition : negativeConditions) {
                if (condition.contains(event)) {
                    return false;
                }
            }
            return true;
        }
    }

    public record Edge(Relation relation, AbstractGenMCWMMGraph graph, Node from, Node to, boolean isNegated, boolean isStatic) {
        @Override
        public String toString() {
            return String.format("%s-%s->%s", from, relation.getNameOrTerm(), to);
        }
        @Override
        public boolean equals(Object obj) { return this == obj; }
    }

    public static class Match {
        private final ViolationPattern pattern;
        private final EventData[] nodeId2EventId;

        public EventData atNode(Node node) { return nodeId2EventId[node.id]; }
        private void setMatch(Node node, EventData id) { nodeId2EventId[node.id] = id; }

        private Match(ViolationPattern pattern, EventData[] mapping) {
            this.pattern = pattern;
            this.nodeId2EventId = mapping;
        }

        public Match(ViolationPattern pattern) {
            this(pattern, new EventData[pattern.nodes.size()]);
            Arrays.fill(nodeId2EventId, null);
        }

        public Match with(Node node, EventData match) {
            assert atNode(node) == null;
            EventData[] updatedMatching = Arrays.copyOf(this.nodeId2EventId, this.nodeId2EventId.length);
            updatedMatching[node.id] = match;
            return new Match(pattern, updatedMatching);
        }

        public EventData[] toArray() { return nodeId2EventId; }

        @Override
        public String toString() {
            return pattern.nodes.stream().map(n -> n + "->" + atNode(n)).collect(Collectors.joining(
                    ", ", "[ ", " ]"
            ));
        }
    }
}
