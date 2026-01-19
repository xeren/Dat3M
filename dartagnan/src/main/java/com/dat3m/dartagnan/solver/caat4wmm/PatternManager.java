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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;

import java.util.*;

import static com.dat3m.dartagnan.configuration.OptionNames.USE_SET_PATTERNS;

@Options
public class PatternManager {

    private static final Logger logger = LogManager.getLogger(PatternManager.class);

    @Option(
            name=USE_SET_PATTERNS,
            description = "Create patterns from base reasons with set predicates",
            secure = true)
    boolean useSets = false;

    private final Map<RelationGraph, Relation> graphs;
    private final List<ViolationPattern> patterns = new ArrayList<>();
    private final Relation rf;
    private final Relation co;

    private PatternManager(final GenMCExecutionGraph graph, final Relation rf, final Relation co) {
        this.graphs = graph.getRelationGraphMap().inverse();
        this.rf = rf;
        this.co = co;
    }

    static PatternManager fromConfig(final Configuration config, final GenMCExecutionGraph graph, final Relation rf, final Relation co) throws InvalidConfigurationException {
        PatternManager patterns = new PatternManager(graph, rf, co);
        config.inject(patterns);
        logger.info("{}: {}", USE_SET_PATTERNS, patterns.useSets);
        return patterns;
    }

    Map.Entry<EventData, Boolean> addFromBaseReasons(final DNF<CAATLiteral> inconsistencyReasons) {
        final List<ViolationPattern> newPatterns = new ArrayList<>();
        EventData backtrackingPoint = null;
        int backtrackingPointId = Integer.MAX_VALUE;
        boolean backtrackingPointIsRel = true;
        REASON_LOOP:
        for (final Conjunction<CAATLiteral> reason : inconsistencyReasons.getCubes()) {
            final ViolationPattern pattern = new ViolationPattern();
            final Map<Integer, ViolationPattern.Node> nodes = new HashMap<>();
            final List<Map.Entry<Edge, AbstractGenMCWMMGraph>> edges = new ArrayList<>(reason.getSize());
            for (final CAATLiteral literal : reason.getLiterals()) {
                final Derivable derivable = literal.getData();
                if (useSets && derivable instanceof final Element condition) {
                    int id = condition.getId();
                    ViolationPattern.Node node = nodes.get(id);
                    if (node == null) {
                        node = pattern.addNode(new ArrayList<>(), new ArrayList<>());
                        nodes.put(id, node);
                    }
                    (literal.isPositive() ? node.positiveConditions() : node.negativeConditions()).add((StaticGenMCWMMSet) literal.getPredicate());
                } else if (literal.isPositive() && derivable instanceof final Edge edge) {
                    edges.add(Map.entry(edge, (AbstractGenMCWMMGraph) literal.getPredicate()));
                } else continue REASON_LOOP;
            }
            for (final Map.Entry<Edge, AbstractGenMCWMMGraph> literal : edges) {
                final Edge edge = literal.getKey();
                final int firstId = edge.getFirst();
                final int secondId = edge.getSecond();
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
                final AbstractGenMCWMMGraph graph = literal.getValue();
                final Relation rel = graphs.get(graph);
                pattern.addRelationGraph(rel, graph);
                pattern.addEdge(rel, firstNode, secondNode, false);
            }
            if (!existsPattern(newPatterns, pattern)) {
                newPatterns.add(pattern);
                final Map.Entry<Edge, AbstractGenMCWMMGraph> anyEdge = edges.get(0);
                final AbstractGenMCWMMGraph graph = anyEdge.getValue();
                final GenMCEventDomain trace = graph.getDomain();
                final Edge edge = anyEdge.getKey();
                final Map.Entry<EventData, Boolean> patternBacktrackingPoint =
                        isViolated(pattern, trace.getObjectById(edge.getFirst()), trace.getObjectById(edge.getSecond()), graphs.get(graph));
                final EventData patternBacktrackingEvent = patternBacktrackingPoint.getKey();
                final int patternBacktrackingPointId = patternBacktrackingEvent.getId();
                final boolean patternBacktrackingPointIsRel = patternBacktrackingPoint.getValue();
                if (patternBacktrackingPointId < backtrackingPointId || (backtrackingPointId == patternBacktrackingPointId && !patternBacktrackingPointIsRel)) {
                    backtrackingPoint = patternBacktrackingEvent;
                    backtrackingPointId = patternBacktrackingPointId;
                    backtrackingPointIsRel = patternBacktrackingPointIsRel;
                }
            }
        }
        patterns.addAll(newPatterns);
        return backtrackingPoint == null ? null : Map.entry(backtrackingPoint, backtrackingPointIsRel);
    }

    private boolean existsPattern(final List<ViolationPattern> patterns, final ViolationPattern pattern) {
        PATTERN_LOOP:
        for (final ViolationPattern otherPattern : patterns) {
            if (pattern.edges.size() == otherPattern.edges.size()) {
                for (int i = 0; i < pattern.edges.size(); ++i) {
                    final ViolationPattern.Edge thisEdge = pattern.edges.get(i);
                    final ViolationPattern.Edge otherEdge = otherPattern.edges.get(i);
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

    public Map.Entry<EventData, Boolean> isViolated(final EventData first, final EventData second, final Relation relation) {
        for (final ViolationPattern pattern : patterns) {
            final Map.Entry<EventData, Boolean> patternBacktrackingPoint = isViolated(pattern, first, second, relation);
            if (patternBacktrackingPoint != null) {
                return patternBacktrackingPoint;
            }
        }
        return null;
    }

    private Map.Entry<EventData, Boolean> isViolated(final ViolationPattern pattern, final EventData first, final EventData second, final Relation relation) {
        final List<ViolationPattern.Edge> rfEdges = pattern.findEdgesByRelation(rf);
        final List<ViolationPattern.Edge> coEdges = pattern.findEdgesByRelation(co);
        final List<ViolationPattern.Edge> relEdges;
        if (relation == rf) {
            relEdges = rfEdges;
        } else if (relation == co) {
            relEdges = coEdges;
        } else {
            relEdges = pattern.findEdgesByRelation(relation);
        }
        for (final ViolationPattern.Edge patternEdge : relEdges) {
            final List<ViolationPattern.Match> matches;
            if (!(matches = pattern.findMatches(patternEdge, first, second)).isEmpty()) {
                EventData backtrackingPoint = null;
                int backtrackingPointId = Integer.MAX_VALUE;
                boolean backtrackingPointIsRel = true;
                for (final ViolationPattern.Match match : matches) {
                    final EventData[] matchEvents = match.toArray();
                    EventData cycleEvent = null;
                    int cycleEventId = -1;
                    boolean cycleEventIsRel = false;
                    for (final EventData event : matchEvents) {
                        final int eventId = event.getId();
                        if (cycleEvent == null || eventId > cycleEventId) {
                            cycleEvent = event;
                            cycleEventId = eventId;
                            cycleEventIsRel = isPartOfRelEdge(event, match, rfEdges, coEdges);
                        } else if (eventId == cycleEventId && isPartOfRelEdge(event, match, rfEdges, coEdges)) {
                            cycleEvent = event;
                            cycleEventIsRel = true;
                        }
                    }
                    if (backtrackingPoint == null || backtrackingPointId > cycleEventId || (backtrackingPointId == cycleEventId && !cycleEventIsRel)) {
                        backtrackingPoint = cycleEvent;
                        backtrackingPointId = cycleEventId;
                        backtrackingPointIsRel = cycleEventIsRel;
                    }
                }
                return Map.entry(backtrackingPoint, backtrackingPointIsRel);
            }
        }
        return null;
    }

    private boolean isPartOfRelEdge(final EventData event, final ViolationPattern.Match match, final List<ViolationPattern.Edge> rfEdges, final List<ViolationPattern.Edge> coEdges) {
        if (isPartOfRelEdge(event, match, rfEdges)) {
            return true;
        }
        return event.getReadFrom() == null && isPartOfRelEdge(event, match, coEdges);
    }

    private boolean isPartOfRelEdge(final EventData event, final ViolationPattern.Match match, final List<ViolationPattern.Edge> edges) {
        for (final ViolationPattern.Edge edge : edges) {
            if (match.atNode(edge.from()) == event || match.atNode(edge.to()) == event) {
                return true;
            }
        }
        return false;
    }

    boolean usesSets() {
        return useSets;
    }
}
