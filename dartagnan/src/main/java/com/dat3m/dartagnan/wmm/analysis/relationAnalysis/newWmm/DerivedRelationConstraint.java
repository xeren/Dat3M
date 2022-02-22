package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

public abstract class DerivedRelationConstraint extends AbstractConstraint implements DefiningConstraint {

    protected final Relation definedRel;
    protected final List<Relation> constrainedRelations;

    public DerivedRelationConstraint(Relation definedRel, List<Relation> dependencies) {
        Preconditions.checkArgument(!dependencies.isEmpty(), "Derived relation mustr have dependencies");
        this.definedRel = Preconditions.checkNotNull(definedRel);
        constrainedRelations = new ArrayList<>(dependencies);
        constrainedRelations.add(definedRel);
    }

    @Override
    public Relation getDefinedRelation() { return definedRel; }

    @Override
    public List<Relation> getConstrainedRelations() {
        return constrainedRelations;
    }

}
