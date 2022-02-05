package com.dat3m.dartagnan.wmm.analysis.relationAnalysis;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface DefiningConstraint extends Constraint {

    // This relation shall be the last element of <getConstrainedRelation>
    default Relation getDefinedRelation() {
        List<Relation> rels = getConstrainedRelations();
        return rels.get(rels.size() - 1);
    }

    // Returns the Knowledge that can be computed about <getDefinedRelation>
    // using the knowledge about <getConstrainedRelations> without <getDefinedRelation>
    // It can be assumed that the provided map does contain all these knowledges.
    Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know);

    // This is used to perform incremental computation of
    // recursive relations.
    Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know);


    // A default implementation cause this method does not get called for defining constraints
    @Override
    default List<Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> know) {
        Knowledge.Delta empty = new Knowledge.Delta();
        return getConstrainedRelations().stream().map(r -> empty).collect(Collectors.toList());
    }
}
