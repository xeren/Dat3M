package com.dat3m.dartagnan.solver.caat4wmm;

import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.RelationGraph;
import com.dat3m.dartagnan.wmm.Relation;

import java.util.*;
import java.util.stream.Collectors;

public class ViolationPattern {

    final Map<Relation, RelationGraph> rel2Graph = new HashMap<>();
    final List<Node> nodes = new ArrayList<>();
    final List<Edge> edges = new ArrayList<>();

    // ------------------------------------------------------------------------------------------
    // Construction

    public Node addNode() {
        final Node node = new Node(nodes.size());
        nodes.add(node);
        return node;
    }

    public Edge addEdge(Relation relation, Node from, Node to, boolean isStatic) {
        final RelationGraph graph = rel2Graph.get(relation);
        assert graph != null;
        final Edge edge = new Edge(relation, graph, from, to, false, isStatic);
        this.edges.add(edge);
        return edge;
    }

    public void addRelationGraph(Relation rel, RelationGraph graph) {
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

    public List<Match> findMatches(Edge edge, int from, int to) {
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
                    final List<Integer> joinedNodeIds = joinAlong(match, matchedNode, nextEdgeToJoin);
                    for (Integer nodeId : joinedNodeIds) {
                        nextMatches.add(match.with(unmatchedNode, nodeId));
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

    private List<Integer> joinAlong(Match partialMatch, Node node, Edge edge) {
        final boolean outgoing = edge.from == node;
        final Node target = outgoing ? edge.to : edge.from;
        final int nodeMatch = partialMatch.atNode(node);

        assert nodeMatch != -1;
        assert partialMatch.atNode(target) == -1;

        final RelationGraph relationGraph = edge.graph;
        final var edges = outgoing ? relationGraph.outEdges(nodeMatch) : relationGraph.inEdges(nodeMatch);
        final List<Integer> matches = new ArrayList<>();
        for (var e : edges) {
            matches.add(outgoing ? e.getSecond() : e.getFirst());
        }

        return matches;
    }

    private void prune(List<Match> matches, Edge edge) {
        final RelationGraph relationGraph = edge.graph;
        matches.removeIf(m -> {
            final int source = m.atNode(edge.from);
            final int target = m.atNode(edge.to);
            return !relationGraph.containsById(source, target);
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

    public record Node(int id) {
        @Override
        public String toString() { return "n#" + id; }

        @Override
        public boolean equals(Object obj) { return this == obj; }
    }

    public record Edge(Relation relation, RelationGraph graph, Node from, Node to, boolean isNegated, boolean isStatic) {
        @Override
        public String toString() {
            return String.format("%s-%s->%s", from, relation.getNameOrTerm(), to);
        }
        @Override
        public boolean equals(Object obj) { return this == obj; }
    }

    public static class Match {
        private final ViolationPattern pattern;
        private final int[] nodeId2EventId;

        public int atNode(Node node) { return nodeId2EventId[node.id]; }
        private void setMatch(Node node, int id) { nodeId2EventId[node.id] = id; }

        private Match(ViolationPattern pattern, int[] mapping) {
            this.pattern = pattern;
            this.nodeId2EventId = mapping;
        }

        public Match(ViolationPattern pattern) {
            this(pattern, new int[pattern.nodes.size()]);
            Arrays.fill(nodeId2EventId, -1);
        }

        public Match with(Node node, int match) {
            assert atNode(node) == -1;
            int[] updatedMatching = Arrays.copyOf(this.nodeId2EventId, this.nodeId2EventId.length);
            updatedMatching[node.id] = match;
            return new Match(pattern, updatedMatching);
        }

        public int[] toArray() { return nodeId2EventId; }

        @Override
        public String toString() {
            return pattern.nodes.stream().map(n -> n + "->" + atNode(n)).collect(Collectors.joining(
                    ", ", "[ ", " ]"
            ));
        }
    }
}
