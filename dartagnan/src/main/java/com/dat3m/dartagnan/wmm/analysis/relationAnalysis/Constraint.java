package com.dat3m.dartagnan.wmm.analysis.relationAnalysis;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//TODO: This class should be part of the wmm package and not of the relation analysis
//TODO 2: Add (i) encoding methods, (ii) active set computation method
/*
    Constraints are of two types:
    - Defining
    - Non-defining
 */
public interface Constraint {

    List<Relation> getConstrainedRelations();

    // Returns a list of Knowledge.Delta's matching the relation list returned by <getConstrainedRelations>
    List<Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> know);
    List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know);



}


interface DefiningConstraint extends Constraint {

    // This relation shall be the last element of <getConstrainedRelation>
    default Relation getDefinedRelation() {
        List<Relation> rels = getConstrainedRelations();
        return rels.get(rels.size() - 1);
    }

    // Returns the Knowledge that can be computed about <getDefinedRelation>
    // using the knowledge about <getConstrainedRelations> without <getDefinedRelation>
    // It can be assumed that the provided map does contain all these knowledges.
    Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> cur);

    // This is used to perform incremental computation of
    // recursive relations.
    Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> cur);


    @Override
    default List<Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> cur) {
        Knowledge.Delta empty = new Knowledge.Delta();
        return getConstrainedRelations().stream().map(r -> empty).collect(Collectors.toList());
    }
}
