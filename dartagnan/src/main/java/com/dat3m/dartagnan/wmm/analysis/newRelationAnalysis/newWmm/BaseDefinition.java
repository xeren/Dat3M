package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public abstract class BaseDefinition extends AbstractDefinition {

    public BaseDefinition(Relation baseRel) {
        super(baseRel, Collections.emptyList());
    }

    @Override
    public String toString() {
        return getTerm();
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know) {
        return new Knowledge.SetDelta(); // This method is never called for base relations as they are never part of an SCC
    }

    @Override
    public List<TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation, Knowledge> know) {
        return Collections.emptyList();
    }

}
