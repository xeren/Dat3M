package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.List;
import java.util.Map;

public interface AxiomaticConstraint extends Constraint {

    // Returns a list of Knowledge.Delta's matching the relation list returned by <getConstrainedRelations>
    // This method is sound even if it returns imprecise information.
    List<Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> know);

    // This method computes the set of tuples the Constraint depends on (for each single dependency)
    // PRECONDITIONS:
    // - The method can assume that the given knowledge <know> is reliable and that AT LEAST this knowledge
    //   will get provided in a subsequent call to <encode>
    // - The method may NOT assume that the given <know> is in any way related to previously computed knowledge
    //   via any of this Constraint's methods.
    //   For all intents and purposes, <know> may have been guessed non-deterministically.
    List<TupleSet> computeActiveSets(Map<Relation, Knowledge> know);


    // Encodes this constraint into a formula.
    // PRECONDITIONS:
    // - The method may assume that <know> is already encoded and hence all its information is reliable.
    // - Furthermore, it is guaranteed that <computeActiveSet> was called before encoding and that
    //   (1) the provided knowledge to <computeActiveSet> was at least as accurate as <know>
    //   and (2) that all tuples returned by <computeActiveSet> have been encoded.
   // BooleanFormula encode(Map<Relation, Knowledge> know, SolverContext ctx);
}
