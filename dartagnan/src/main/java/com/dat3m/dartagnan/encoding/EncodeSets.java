package com.dat3m.dartagnan.encoding;

import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.wmm.Constraint;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.dat3m.dartagnan.configuration.OptionNames.REDUCE_ACYCLICITY_ENCODE_SETS;

@Options
public final class EncodeSets {

    private static final Logger logger = LogManager.getLogger(EncodeSets.class);

    // =========================== Configurables ===========================

    @Option(
            name = REDUCE_ACYCLICITY_ENCODE_SETS,
            description = "Use must-information to.",
            secure = true)
    private boolean reduceAcyclicity = true;

    // =====================================================================

    private final Wmm memoryModel;
    private final ExecutionAnalysis exec;
    private final Map<Relation, Set<Tuple>> activeSets = new HashMap<>();

    // =====================================================================

    private EncodeSets(Wmm m, Context a) {
        memoryModel = m;
        exec = a.requires(ExecutionAnalysis.class);
        a.requires(RelationAnalysis.class);
    }

    public static EncodeSets fromConfig(Wmm memoryModel, Context analysisContext, Configuration config) throws InvalidConfigurationException {
        EncodeSets a = new EncodeSets(memoryModel, analysisContext);
        config.inject(a);
        a.run();
        return a;
    }

    public Set<Tuple> get(Relation relation) {
        return activeSets.get(relation);
    }

    private void run() {
        for (Relation r : memoryModel.getRelations()) {
            activeSets.put(r, new HashSet<>());
        }
        Visitor v = new Visitor();
        Map<Relation, List<Stream<Tuple>>> queue = new HashMap<>();
        for (Axiom ax : memoryModel.getAxioms()) {
            for (Map.Entry<Relation, Stream<Tuple>> e : ax.visit(v).entrySet()) {
                queue.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
            }
        }
        while (!queue.isEmpty()) {
            Relation r = queue.keySet().iterator().next();
            Set<Tuple> s = activeSets.get(r);
            List<Tuple> c = new ArrayList<>();
            for (Stream<Tuple> news : queue.remove(r)) {
                news.filter(s::add).forEach(c::add);
            }
            if (!c.isEmpty()) {
                v.news = c;
                for (Map.Entry<Relation, Stream<Tuple>> e : r.visit(v).entrySet()) {
                    queue.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
                }
            }
        }
    }

    private final class Visitor implements Constraint.Visitor<Map<Relation, Stream<Tuple>>> {
        private List<Tuple> news;

        @Override
        public Map<Relation, Stream<Tuple>> visitEmptiness(Axiom ax) {
            Relation r = ax.getRelation();
            return Map.of(r, r.getMaxTupleSet().stream());
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitIrreflexivity(Axiom ax) {
            Relation r = ax.getRelation();
            return Map.of(r, r.getMaxTupleSet().stream().filter(Tuple::isLoop));
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitAcyclicity(Axiom ax) {
            Relation rel = ax.getRelation();
            logger.info("Computing encodeTupleSet for acyclic {}", rel);
            // ====== Construct [Event -> Successor] mapping ======
            Map<Event, Collection<Event>> succMap = new HashMap<>();
            TupleSet may = rel.getMaxTupleSet();
            for (Tuple t : may) {
                succMap.computeIfAbsent(t.getFirst(), key -> new ArrayList<>()).add(t.getSecond());
            }
            // ====== Compute SCCs ======
            DependencyGraph<Event> depGraph = DependencyGraph.from(succMap.keySet(), succMap);
            TupleSet result = new TupleSet();
            for (Set<DependencyGraph<Event>.Node> scc : depGraph.getSCCs()) {
                for (DependencyGraph<Event>.Node node1 : scc) {
                    for (DependencyGraph<Event>.Node node2 : scc) {
                        Tuple t = new Tuple(node1.getContent(), node2.getContent());
                        if (may.contains(t)) {
                            result.add(t);
                        }
                    }
                }
            }
            logger.info("encodeTupleSet size: {}", result.size());
            if (reduceAcyclicity) {
                reduceWithMinSets(rel.getMinTupleSet(), result);
                logger.info("reduced encodeTupleSet size: {}", result.size());
            }
            return Map.of(rel, result.stream());
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitForceEncoding(Axiom ax) {
            return visitEmptiness(ax);
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitUnion(Relation rel, Relation r1, Relation r2) {
            return Map.of(r1, lower(news, r1), r2, lower(news, r2));
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitIntersection(Relation rel, Relation r1, Relation r2) {
            return Map.of(r1, upper(news, r1), r2, upper(news, r2));
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitDifference(Relation rel, Relation r1, Relation r2) {
            return Map.of(r1, upper(news, r1), r2, lower(news, r2));
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitComposition(Relation rel, Relation r1, Relation r2) {
            if (news.isEmpty()) {
                return Map.of();
            }
            TupleSet set1 = new TupleSet();
            TupleSet set2 = new TupleSet();
            TupleSet may1 = r1.getMaxTupleSet();
            TupleSet may2 = r2.getMaxTupleSet();
            TupleSet must1 = r1.getMinTupleSet();
            TupleSet must2 = r2.getMinTupleSet();
            for (Tuple t : news) {
                Event e = t.getSecond();
                for (Tuple t1 : may1.getByFirst(t.getFirst())) {
                    Tuple t2 = new Tuple(t1.getSecond(), e);
                    if (may2.contains(t2)) {
                        if (!must1.contains(t1)) {
                            set1.add(t1);
                        }
                        if (!must2.contains(t2)) {
                            set2.add(t2);
                        }
                    }
                }
            }
            return Map.of(r1, set1.stream(), r2, set2.stream());
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitDomainIdentity(Relation rel, Relation r1) {
            TupleSet may1 = r1.getMaxTupleSet();
            return Map.of(r1, news.stream().flatMap(t -> may1.getByFirst(t.getFirst()).stream()));
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitRangeIdentity(Relation rel, Relation r1) {
            TupleSet may1 = r1.getMaxTupleSet();
            return Map.of(r1, news.stream().flatMap(t -> may1.getBySecond(t.getSecond()).stream()));
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitInverse(Relation rel, Relation r1) {
            return Map.of(r1, news.stream().map(Tuple::getInverse));
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitRecursive(Relation rel, Relation other) {
            return Map.of(other, news.stream());
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitTransitiveClosure(Relation rel, Relation r1) {
            HashSet<Tuple> factors = new HashSet<>();
            TupleSet may = rel.getMaxTupleSet();
            TupleSet must = rel.getMinTupleSet();
            for (Tuple t : news) {
                Event e = t.getSecond();
                for (Tuple t1 : may.getByFirst(t.getFirst())) {
                    Tuple t2 = new Tuple(t1.getSecond(), e);
                    if (may.contains(t2)) {
                        if (!must.contains(t1)) {
                            factors.add(t1);
                        }
                        if (!must.contains(t2)) {
                            factors.add(t2);
                        }
                    }
                }
            }
            return Map.of(rel, factors.stream(), r1, lower(news, r1));
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitReflexiveTransitiveClosure(Relation rel, Relation r1) {
            return visitTransitiveClosure(rel, r1);
        }

        @Override
        public Map<Relation, Stream<Tuple>> visitCriticalSections(Relation rscs) {
            List<Tuple> queue = new ArrayList<>();
            TupleSet may = rscs.getMaxTupleSet();
            for (Tuple tuple : news) {
                Event lock = tuple.getFirst();
                Event unlock = tuple.getSecond();
                for (Tuple t : may.getBySecond(unlock)) {
                    Event e = t.getFirst();
                    if (lock.getCId() < e.getCId() && e.getCId() < unlock.getCId()) {
                        queue.add(t);
                    }
                }
                for (Tuple t : may.getByFirst(lock)) {
                    Event e = t.getSecond();
                    if (lock.getCId() < e.getCId() && e.getCId() < unlock.getCId()) {
                        queue.add(t);
                    }
                }
            }
            return Map.of(rscs, queue.stream());
        }
    }

    private Stream<Tuple> lower(Collection<Tuple> news, Relation relation) {
        return news.stream().filter(relation.getMaxTupleSet()::contains);
    }

    private Stream<Tuple> upper(Collection<Tuple> news, Relation relation) {
        Collection<Tuple> set = relation.getMinTupleSet();
        return news.stream().filter(t -> !set.contains(t));
    }

    private void reduceWithMinSets(TupleSet minSet, Set<Tuple> encodeSet) {
        /*
            ASSUMPTION: MinSet is acyclic!
            IDEA:
                Edges that are (must-)transitively implied do not need to get encoded.
                For this, we compute a (must-)transitive closure and a (must-)transitive reduction of must(rel).
                The difference "must(rel)+ \ red(must(rel))" does not net to be encoded.
                Note that it this is sound if the closure gets underapproximated and/or the reduction
                gets over approximated.
            COMPUTATION:
                (1) We compute an approximate (must-)transitive closure of must(rel)
                    - must(rel) is likely to be already transitive per thread (due to mostly coming from po)
                      Hence, we get a reasonable approximation by closing transitively over thread-crossing edges only.
                (2) We compute a (must) transitive reduction of the transitively closed must(rel)+.
                    - Since must(rel)+ is transitive, it suffice to check for each edge (a, c) if there
                      is an intermediate event b such that (a, b) and (b, c) are in must(rel)+
                      and b is implied by either a or c.
                    - It is possible to reduce must(rel) but that may give a less precise result.
         */
        // (1) Approximate transitive closure of minSet (only gets computed when crossEdges are available)
        List<Tuple> crossEdges = minSet.stream()
                .filter(t -> t.isCrossThread() && !t.getFirst().is(Tag.INIT))
                .collect(Collectors.toList());
        TupleSet transMinSet = crossEdges.isEmpty() ? minSet : new TupleSet(minSet);
        for (Tuple crossEdge : crossEdges) {
            Event e1 = crossEdge.getFirst();
            Event e2 = crossEdge.getSecond();
            List<Event> ingoing = new ArrayList<>();
            ingoing.add(e1); // ingoing events + self
            minSet.getBySecond(e1).stream().map(Tuple::getFirst)
                    .filter(e -> exec.isImplied(e, e1))
                    .forEach(ingoing::add);
            List<Event> outgoing = new ArrayList<>();
            outgoing.add(e2); // outgoing edges + self
            minSet.getByFirst(e2).stream().map(Tuple::getSecond)
                    .filter(e -> exec.isImplied(e, e2))
                    .forEach(outgoing::add);
            for (Event in : ingoing) {
                for (Event out : outgoing) {
                    transMinSet.add(new Tuple(in, out));
                }
            }
        }
        // (2) Approximate reduction of transitive must-set: red(must(r)+).
        // Note: We reduce the transitive closure which may have more edges
        // that can be used to perform reduction
        TupleSet reduct = TupleSet.approximateTransitiveMustReduction(exec, transMinSet);
        // Remove (must(r)+ \ red(must(r)+)
        encodeSet.removeIf(t -> transMinSet.contains(t) && !reduct.contains(t));
    }
}
