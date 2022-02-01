package com.dat3m.dartagnan.wmm.analysis.relationAnalysis;

import com.dat3m.dartagnan.utils.dependable.DependencyGraph;

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


    public NewRelationAnalysis() {
        //TODO: Take input parameters
        dependencyGraph = DependencyGraph.from(relations);
    }

    private List<Constraint> getConstraintsForRelation(Relation rel) {
        //TODO
        return null;
    }


    public Map<Relation, Knowledge> algorithm() {
        Map<Relation, Knowledge> know = computeInitialKnowledge();
        computeKnowledgeClosure(know);
        return know;
    }


    // ======================= Derivations and defining knowledge =======================

    private Knowledge.SetDelta setJoin(Knowledge k, Knowledge.SetDelta delta) {
        // TODO
        return new Knowledge.SetDelta();
    }

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

    private List<DerivationTask> processDerivationTask(DerivationTask task, Map<Relation, Knowledge> curKnow) {
        Relation target = task.to;
        Relation from = task.from;

        Knowledge.SetDelta newDelta = setJoin(curKnow.get(target),
                target.getDefiningConstraint().computeIncrementalDefiningKnowledge(from, task.delta, curKnow));
        if (newDelta.isEmpty()) {
            // Nothing has changed, so we don't create new tasks
            return Collections.emptyList();
        }
        return dependencyGraph.get(target).getDependents()
                .stream().map(DependencyGraph.Node::getContent)
                .map(dependent -> new DerivationTask(target, dependent, newDelta))
                .collect(Collectors.toList());

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


    // ======================= Knowledge closure =======================

    // Update <k> by adding <delta> and return the actual change <deltaNew>
    private Knowledge.Delta join(Knowledge k, Knowledge.Delta delta) {
        // TODO
        if (delta.isEmpty()) {
            return delta;
        }
        return new Knowledge.Delta();
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
        Knowledge.Delta newDelta = join(know.get(rel), task.delta);
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




    // =================================== Internal Task classes ============================
    // TODO: In Java 14+, we could actually use records here

    // -------------------- Derivations --------------------

    private static class DerivationTask {
        Relation from;
        Relation to;
        Knowledge.SetDelta delta;

        public DerivationTask(Relation from, Relation to, Knowledge.SetDelta delta) {
            this.from = from;
            this.to = to;
            this.delta = delta;
        }
    }

    // --------------------- Knowledge closure --------------------

    private static class RelToConstraintTask {
        Relation from;
        Constraint to;
        Knowledge.Delta delta;

        public RelToConstraintTask(Relation from, Constraint to, Knowledge.Delta delta) {
            this.from = from;
            this.to = to;
            this.delta = delta;
        }
    }

    private static class ConstraintToRelTask {
        Constraint from;
        Relation to;
        Knowledge.Delta delta;

        public ConstraintToRelTask(Constraint from, Relation to, Knowledge.Delta delta) {
            this.from = from;
            this.to = to;
            this.delta = delta;
        }
    }

}
