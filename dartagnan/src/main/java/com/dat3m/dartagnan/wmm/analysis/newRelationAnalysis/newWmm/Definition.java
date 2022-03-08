package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.List;
import java.util.Map;

/*
    Definitions are of two types:
        - Base relation definitions
        - Derived relation definitions
    Each relation has exactly one associated Definition.
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

    /*
        Returns the Knowledge that can be computed about <getDefinedRelation>
        using the knowledge about <getDependencies>. The provided map
        contains all necessary knowledge.
     */
    Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know);

    // This is used to perform incremental computation of recursive relations.
    Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know);

    /*
        The evaluation of <activeSet> depends on evaluations of tuples of this constraint's dependencies.
        This method computes the tuples of each immediate dependency that can affect the evaluation of <activeSet>
     */
    List<TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation, Knowledge> know);

    /*
        Returns an SMT-Variable for <tuple>. The definition of that variable may not yet be encoded.
        It is allowed to use knowledge or any other information to simplify the returned expression
        (e.g. "false" if the relation never holds).
     */
    BooleanFormula getSMTVar(Tuple tuple, Knowledge kRel, EncodingContext ctx);

    /*
        Encodes the definition of the tuples in <toBeEncoded>. If necessary, this method may encode more tuples.
        In some cases it is impossible to encode only part of the relation, for example when more global constraints
        like functionality (e.g. rf) or totality (e.g. co) are required.
        In those cases, the class should also implement AxiomaticConstraint and provide the encoding via
        <encodeAxiom> instead. <encodeDefinitions> may then simply return "true".
     */
    BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation, Knowledge> know, EncodingContext ctx);

}
