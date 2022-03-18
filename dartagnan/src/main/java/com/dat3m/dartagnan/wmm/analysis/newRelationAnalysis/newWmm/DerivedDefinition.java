package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import java.util.List;
import java.util.Map;

public abstract class DerivedDefinition extends AbstractDefinition {


    public DerivedDefinition(Relation definedRel, List<Relation> dependencies) {
        super(definedRel, dependencies);
        Preconditions.checkArgument(!dependencies.isEmpty(), "A derived relation must have dependencies.");
    }

    protected abstract String getOperationSymbol();

    private boolean recursionFlag = false;
    @Override
    public String getTerm() {
        if (recursionFlag) {
            return "__this";
        }
        List<Relation> deps = dependencies;
        recursionFlag = true;
        String term = deps.size() > 1 ?
                "(" + String.join(getOperationSymbol(), Iterables.transform(dependencies, Relation::getNameOrTerm)) + ")"
                : deps.get(0).getNameOrTerm() + getOperationSymbol();
        recursionFlag = false;
        return term;
    }


    protected abstract Map<Relation,Knowledge.Delta> bottomUpKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know);
    protected abstract Map<Relation,Knowledge.Delta> topDownKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know);

    @Override
    public Map<Relation,Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        assert getConstrainedRelations().contains(changed);
        return changed == getDefinedRelation() ?
                topDownKnowledgeClosure(changed, delta, know)
                : bottomUpKnowledgeClosure(changed, delta, know);
    }

    @Override
    public Map<Relation,TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        // Derived relations have no inherent active sets
        return Map.of();
    }

    @Override
    public String toString() {
        if (definedRelation.isNamed()) {
            return definedRelation.getName() + " := " + getTerm();
        } else {
            return getTerm();
        }
    }
}
