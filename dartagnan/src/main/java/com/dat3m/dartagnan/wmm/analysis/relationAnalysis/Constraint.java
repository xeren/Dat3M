package com.dat3m.dartagnan.wmm.analysis.relationAnalysis;


import java.util.List;
import java.util.Map;

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
    // These methods are sound even if they return imprecise deltas (e.g. empty ones).
    // TODO: This method is actually not needed for defining constraints so we might want to move it
    //  into a separate interface
    List<Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> know);

    List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know);



}


