package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public abstract class BaseRelationConstraint extends AbstractConstraint implements DefiningConstraint {
    protected final Relation baseRel;

    public BaseRelationConstraint(Relation baseRel) {
        this.baseRel = baseRel;
    }

    @Override
    public Relation getDefinedRelation() {
        return baseRel;
    }

    @Override
    public List<Relation> getConstrainedRelations() {
        return Collections.singletonList(baseRel);
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know) {
        return new Knowledge.SetDelta(); // This method is never called for base relations as they are never part of an SCC
    }
}
