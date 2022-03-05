package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import java.util.*;

public abstract class DerivedDefinition extends AbstractConstraint implements Definition {

    protected final Relation definedRel;
    protected final List<Relation> constrainedRelations;

    public DerivedDefinition(Relation definedRel, List<Relation> dependencies) {
        Preconditions.checkArgument(!dependencies.isEmpty(), "Derived relation must have dependencies.");
        this.definedRel = Preconditions.checkNotNull(definedRel);
        constrainedRelations = new ArrayList<>(dependencies);
        constrainedRelations.add(definedRel);
    }

    public DerivedDefinition(Relation definedRel, Relation... dependencies) {
        this(definedRel, Arrays.asList(dependencies));
    }

    @Override
    public Relation getDefinedRelation() { return definedRel; }

    @Override
    public List<Relation> getConstrainedRelations() {
        return constrainedRelations;
    }

    protected abstract String getOperationSymbol();

    private boolean recursionFlag = false;
    @Override
    public String getTerm() {
        if (recursionFlag) {
            return "__this";
        }
        List<Relation> deps = getDependencies();
        recursionFlag = true;
        String term = deps.size() > 1 ?
                String.join(getOperationSymbol(), Iterables.transform(getDependencies(), Relation::getNameOrTerm))
                : deps.get(0).getNameOrTerm() + getOperationSymbol();
        recursionFlag = false;
        return term;
    }


    protected abstract List<Knowledge.Delta> bottomUpKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know);
    protected abstract List<Knowledge.Delta> topDownKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know);

    @Override
    public List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        assert getConstrainedRelations().contains(changed);
        return changed == getDefinedRelation() ?
                topDownKnowledgeClosure(changed, delta, know)
                : bottomUpKnowledgeClosure(changed, delta, know);
    }

    @Override
    public List<TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        // Derived relations have no inherent active sets
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        if (definedRel.isNamed()) {
            return definedRel.getName() + " := " + getTerm();
        } else {
            return getTerm();
        }
    }
}
