package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm;


import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;
import java.util.Map;

//TODO: This class should be part of the wmm package and not of the relation analysis
//TODO 2: Add (i) encoding methods, (ii) active set computation method
/*
    The Constraint interface is the core component of the Relation Analysis.
    Its implementation can be roughly categorized into two categories:
        - Definitions (for base relations and derived relations)
        - Axioms (e.g. the CAT axioms, assumptions, witnesses, symmetries...)
 */
public interface Constraint {

    List<Relation> getConstrainedRelations();

    // This method gets called before any knowledge computation takes place to give the context
    // of the computation. The callee shall guarantee a fresh state.
    void initializeToTask(VerificationTask task, Context analysisContext);

    // Returns a list of Knowledge.Delta's matching the relation list returned by <getConstrainedRelations>
    // This method is sound even if it returns imprecise deltas (e.g. empty ones).
    // PRECONDITIONS: The callee can assume that
    // (1) a knowledge initialization method has been called before:
    //      - AxiomaticConstraint.computeInitialKnowledgeClosure
    //      - OR DefiningConstraint.computeInitialDefiningKnowledge
    // (2) the given knowledge <know> is at least as large as it was when the method was called the last time
    //      (or larger than the initial knowledge (1) for the very first call).
    List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know);

    // This method computes the set of tuples the Constraint depends on (for each single dependency)
    // PRECONDITIONS:
    // - The method can assume that the given knowledge <know> is reliable and that AT LEAST this knowledge
    //   will get provided in a subsequent call to <encode>
    // - The method may NOT assume that the given <know> is in any way related to previously computed knowledge
    //   via any of this Constraint's methods.
    //   For all intents and purposes, <know> may have been guessed non-deterministically.
    List<TupleSet> computeActiveSets(Map<Relation, Knowledge> know);

}


