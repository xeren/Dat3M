package com.dat3m.dartagnan.wmm.analysis.relationAnalysis;

import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.stream.Collectors;

// There are two phases when it comes to computing knowledge
// (1) An initialization phase that computes some sound knowledge for all relations (in whatever way)
//      - This phase will work bottom up along the stratification
//      - It will mainly involve defining constraints (defining equations in the CAT
//        as well as defining constraints for the base relations coming from the anarchic semantics)
//      - It will perform computations that are non-monotonic w.r.t. the knowledge domain.
//      - (*)Potential Optimization:
//        We might want to incorporate local refinements on each strata before going to the next
// (2) A refinement phase, that uses the current knowledge and all the constraints (in particular the non-defining ones)
//     to generate new knowledge.
//     - This phase will cause bottom-up and top-down propagations
//     - All the propagations are monotonic w.r.t. the knowledge-ordering
//  TODO: Doing these two steps in succession is not sufficient
//   We need to intertwine them (computing knowledge closures on each stratum(*)),
//   or we need to alternate the (1) and (2) until we get a fixed point
//   For non-recursive WMMs, doing (1) then (2) is sufficient.
public class NewRelationAnalysis {

    private final Set<Constraint> constraints = new HashSet<>();
    private final List<Relation> relations = new ArrayList<>();
    private final DependencyGraph<Relation> dependencyGraph;
    private final Map<Relation, Set<Constraint>> relToConstraintsMap;

    private Map<Relation, Knowledge.Delta> discrepancyMap = new HashMap<>();


    public NewRelationAnalysis() {
        //TODO: Take input parameters

        relToConstraintsMap = new HashMap<>(relations.size());
        relations.forEach(r -> relToConstraintsMap.put(r,
                constraints.stream().filter(c -> c.getConstrainedRelations().contains(r)).collect(Collectors.toSet())
        ));
        dependencyGraph = DependencyGraph.from(relations);
    }

    private Set<Constraint> getConstraintsForRelation(Relation rel) {
        return relToConstraintsMap.get(rel);
    }


    public Map<Relation, Knowledge> algorithm() {
        Map<Relation, Knowledge> know = computeInitialKnowledge();
        computeKnowledgeClosure(know);
        this.discrepancyMap = computeDiscrepancyAndUpdate(know);
        return know;
    }


    private Map<Relation, Knowledge.Delta> computeDiscrepancyAndUpdate(Map<Relation, Knowledge> know) {
        // Proceed bottom-up and
        // (1) compute discrepancy between knowledge of strata
        // (2) update information of upper stratum if lower stratum has more knowledge (could happen due to incomplete propagation)
        Map<Relation, Knowledge.Delta> discrepancyMap = new HashMap<>(know.size());

        for (Set<DependencyGraph<Relation>.Node> scc : dependencyGraph.getSCCs()) {
            Set<Relation> stratum = scc.stream().map(DependencyGraph.Node::getContent).collect(Collectors.toSet());
            if (stratum.size() == 1 && stratum.stream().findAny().get().getDependencies().isEmpty()) {
                discrepancyMap.put(stratum.stream().findAny().get(), new Knowledge.Delta());
                continue; // We have a base relation, so we keep the current knowledge
            }
            Map<Relation, Knowledge> knowCopy = new HashMap<>(know); // Copy since the next method updates in-place
            computeDefiningKnowledge(stratum, knowCopy);

            // <knowCopy> should contain less knowledge than <know> (if it contains more, we can add it to <know>)
            for (Relation rel : stratum) {
                Knowledge full = know.get(rel);
                Knowledge derived = knowCopy.get(rel);
                TupleSet deltaMay = new TupleSet();
                TupleSet deltaMust = new TupleSet();

                for (Tuple t : Sets.symmetricDifference(full.getMaySet(), derived.getMaySet())) {
                    if (derived.getMaySet().contains(t)) {
                        deltaMay.add(t);
                    } else {
                        full.getMaySet().add(t); // We derived more knowledge and can update <full>
                    }
                }

                for (Tuple t : Sets.symmetricDifference(full.getMustSet(), derived.getMustSet())) {
                    if (full.getMustSet().contains(t)) {
                        deltaMust.add(t);
                    } else {
                        full.getMustSet().add(t); // We derived more knowledge and can update <full>
                    }
                }

                discrepancyMap.put(rel, new Knowledge.Delta(deltaMay, deltaMust));
            }
        }

        return discrepancyMap;
    }


    // ======================= Derivations and defining knowledge =======================

    /**
     * Computes the defining knowledge of a single stratum of relations, given knowledge about the previous strata.
     * Updates the provided knowledge map with the knowledge about the stratum
     * (overwrites all knowledge about that stratum!)
     * Unlike the typical knowledge propagation, this takes into account the lfp semantics
     * of fixed points.
     * @param stratum The non-empty stratum of relations (a single SCC in the dependency graph)
     * @param know The knowledge about previous strata
     */
    private void computeDefiningKnowledge(Set<Relation> stratum, Map<Relation, Knowledge> know) {
        assert !stratum.isEmpty();
        if (stratum.size() == 1) {
            // Stratum with single relation
            Relation rel = stratum.stream().findAny().get();
            know.put(rel, rel.getDefiningConstraint().computeInitialDefiningKnowledge(know));
        } else {
            // Stratum with mutually recursive relations

            // Initialize all relations in the stratum
            know.keySet().removeAll(stratum);
            Queue<Relation> initQueue = new ArrayDeque<>(stratum);
            while (!initQueue.isEmpty()) {
                Relation rel = initQueue.poll();
                if (rel.isRecursive()) {
                    know.put(rel, Knowledge.newEmptySet());
                } else if (know.keySet().containsAll(rel.getDependencies())) {
                    know.put(rel, rel.getDefiningConstraint().computeInitialDefiningKnowledge(know));
                } else {
                    initQueue.add(rel);
                }
            }

            // Initialize propagation tasks within the stratum
            Queue<DerivationTask> workQueue = new ArrayDeque<>();
            stratum.stream().filter(Relation::isRecursive).forEach(r -> {
                for (Relation dep : r.getDependencies()) {
                    workQueue.add(new DerivationTask(dep, r, know.get(dep).asSetDelta()));
                }
            });

            // Propagate within the stratum until the lfp is reached
            while (!workQueue.isEmpty()) {
                processDerivationTask(workQueue.poll(), know).stream()
                        .filter(task -> stratum.contains(task.to))
                        .forEach(workQueue::add);
            }
        }
    }

    private Map<Relation, Knowledge> computeInitialKnowledge() {
        // Computes the initial knowledge by going over all strata in topological order
        Map<Relation, Knowledge> know = new HashMap<>(relations.size());
        for (Set<DependencyGraph<Relation>.Node> scc : dependencyGraph.getSCCs()) {
            Set<Relation> stratum = scc.stream().map(DependencyGraph.Node::getContent).collect(Collectors.toSet());
            computeDefiningKnowledge(stratum, know);
        }

        return know;
    }

    private List<DerivationTask> processDerivationTask(DerivationTask task, Map<Relation, Knowledge> curKnow) {
        Relation target = task.to;
        Relation from = task.from;

        Knowledge.SetDelta newDelta = curKnow.get(target).joinSet(
                target.getDefiningConstraint().computeIncrementalDefiningKnowledge(from, task.delta, curKnow)
        );
        if (newDelta.isEmpty()) {
            // Nothing has changed, so we don't create new tasks
            return Collections.emptyList();
        }
        return dependencyGraph.get(target).getDependents()
                .stream().map(DependencyGraph.Node::getContent)
                .map(dependent -> new DerivationTask(target, dependent, newDelta))
                .collect(Collectors.toList());

    }



    // ======================= Knowledge closure =======================


    private void computeKnowledgeClosure(Map<Relation, Knowledge> know) {
        Queue<RelToConstraintTask> rQueue = new ArrayDeque<>();
        Queue<ConstraintToRelTask> cQueue = new ArrayDeque<>(initializeKnowledgeClosure(constraints, know));

        // TODO: Multiple tasks with same (from, to) pair should get merged into a single task (with combined deltas)
        do {
            while (!cQueue.isEmpty()) {
                rQueue.addAll(processConstraintTask(cQueue.poll(), know));
            }
            while (!rQueue.isEmpty()) {
                cQueue.addAll(processRelationTask(rQueue.poll(), know));
            }
        } while (!cQueue.isEmpty());

    }

    private List<ConstraintToRelTask> initializeKnowledgeClosure(Set<Constraint> constraints, Map<Relation, Knowledge> know) {
        // Initialization phase
        List<ConstraintToRelTask> result = new ArrayList<>();
        for (Constraint c : constraints) {
            if (c instanceof DefiningConstraint) {
                // We assume that all defining constraints have been evaluated before
                // to get the initial approximation
                continue;
            }
            List<Knowledge.Delta> deltas = c.computeInitialKnowledgeClosure(know);
            List<Relation> rels = c.getConstrainedRelations();
            assert deltas.size() == rels.size();

            for (int i = 0; i < deltas.size(); i++) {
                if (!deltas.get(i).isEmpty()) {
                    result.add(new ConstraintToRelTask(c, rels.get(i), deltas.get(i)));
                }
            }
        }
        return result;
    }

    private List<RelToConstraintTask> processConstraintTask(ConstraintToRelTask task, Map<Relation, Knowledge> know) {
        Relation rel = task.to;
        Knowledge.Delta newDelta = know.get(rel).join(task.delta);
        if (newDelta.isEmpty()) {
            return Collections.emptyList();
        }

        return getConstraintsForRelation(rel).stream()
                    .filter(c -> c != task.from)
                    .map(c -> new RelToConstraintTask(rel, c, newDelta))
                    .collect(Collectors.toList());
    }

    private List<ConstraintToRelTask> processRelationTask(RelToConstraintTask task, Map<Relation, Knowledge> know) {
        Constraint c = task.to;
        List<Relation> rels = c.getConstrainedRelations();
        List<Knowledge.Delta> newDeltas = c.computeIncrementalKnowledgeClosure(task.from, task.delta, know);
        assert rels.size() == newDeltas.size();

        List<ConstraintToRelTask> result = new ArrayList<>(rels.size());
        for (int i = 0; i < rels.size(); i++) {
            if (!newDeltas.get(i).isEmpty()) {
                result.add(new ConstraintToRelTask(c, rels.get(i), newDeltas.get(i)));
            }
        }

        return result;
    }




    // =================================== Internal Task classes ============================
    // TODO: In Java 14+, we could actually use records here

    private static abstract class Task<TFrom, TTo, TDelta> {
        final TFrom from;
        final TTo to;
        final TDelta delta;

        public Task(TFrom from, TTo to, TDelta delta) {
            this.from = from;
            this.to = to;
            this.delta = delta;
        }
    }

    // --------------------- Derivations --------------------

    private static class DerivationTask extends Task<Relation, Relation, Knowledge.SetDelta> {
        public DerivationTask(Relation from, Relation to, Knowledge.SetDelta delta) {
            super(from, to, delta);
        }
    }

    // --------------------- Knowledge closure --------------------

    private static class RelToConstraintTask extends Task<Relation, Constraint, Knowledge.Delta> {
        public RelToConstraintTask(Relation from, Constraint to, Knowledge.Delta delta) {
            super(from, to, delta);
        }
    }

    private static class ConstraintToRelTask extends Task<Constraint, Relation, Knowledge.Delta> {
        public ConstraintToRelTask(Constraint from, Relation to, Knowledge.Delta delta) {
            super(from, to, delta);
        }
    }

}
