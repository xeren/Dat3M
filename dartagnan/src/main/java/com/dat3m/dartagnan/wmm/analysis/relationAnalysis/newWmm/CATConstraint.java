package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import java.util.Collections;
import java.util.List;

public abstract class CATConstraint extends AbstractConstraint implements AxiomaticConstraint {
    protected final Relation rel;

    public CATConstraint(Relation baseRel) {
        this.rel = baseRel;
    }

    public Relation getConstrainedRelation() { return rel; }

    @Override
    public List<Relation> getConstrainedRelations() {
        return Collections.singletonList(rel);
    }


}
