package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.List;
import java.util.Map;

/*
    DefiningConstraints are of two types:
        - Base relation constraints
        - Derived relation constraints
    Each relation has exactly one associated defining constraint.
    A defining constraint is responsible for creating SMT-variables
 */
public interface Definition extends Constraint {

    String getTerm();

    // This relation shall be the last element of <getConstrainedRelation>
    default Relation getDefinedRelation() {
        List<Relation> rels = getConstrainedRelations();
        return rels.get(rels.size() - 1);
    }

    default List<Relation> getDependencies() {
        List<Relation> rels = getConstrainedRelations();
        return rels.subList(0, rels.size() - 1);
    }

    // Returns the Knowledge that can be computed about <getDefinedRelation>
    // using the knowledge about <getConstrainedRelations> without <getDefinedRelation>
    // It can be assumed that the provided map does contain all these knowledges.
    Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know);

    // This is used to perform incremental computation of recursive relations.
    Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know);

    // The evaluation of <activeSet> depends on evaluations of tuples of this constraint's dependencies
    // This method computes the tuples of each immediate dependency that can affect the evaluation of <activeSet>
    List<TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation, Knowledge> know);

    // Returns an SMTVar for <tuple>. This variable may not yet be encoded.
    // It is allowed to use knowledge or any other information to
    BooleanFormula getSMTVar(Tuple tuple, Knowledge kRel, EncodingContext ctx);

}
