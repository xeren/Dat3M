package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.AbstractConstraint;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.AxiomaticConstraint;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Assumption extends AbstractConstraint implements AxiomaticConstraint {

    private final Relation relation;
    private final TupleSet enabled;
    private final TupleSet disabled;

    public Assumption(Relation rel, TupleSet disabled, TupleSet enabled) {
        this.relation = rel;
        this.enabled = enabled;
        this.disabled = disabled;
    }

    @Override
    public List<Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> know) {
        return Collections.singletonList(new Knowledge.Delta(disabled, enabled));
    }

    @Override
    public List<Relation> getConstrainedRelations() {
        return Collections.singletonList(relation);
    }

    @Override
    public List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        return Collections.emptyList();
    }

    @Override
    public List<TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        return Collections.emptyList();
    }
}
