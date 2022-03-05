package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.DerivedDefinition;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// Example implementation of an n-ary union
public class Union extends DerivedDefinition {

    public Union(Relation definedRel, List<Relation> unionRels) {
        super(definedRel, unionRels);
    }

    public Union(Relation definedRel, Relation... dependencies) { this(definedRel, Arrays.asList(dependencies)); }

    @Override
    protected String getOperationSymbol() {
        return " + ";
    }

    @Override
    protected List<Knowledge.Delta> bottomUpKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        final List<Relation> deps = getDependencies();
        List<Knowledge.Delta> deltas = new ArrayList<>(deps.size() + 1);
        for (int i = 0; i < deps.size(); i++) {
            deltas.add(new Knowledge.Delta());
        }
        // enabled sets always propagate upwards
        Knowledge.Delta defDelta = new Knowledge.Delta(new TupleSet(), delta.getEnabledSet());
        deltas.add(defDelta);

        if (delta.getDisabledSet().isEmpty()) {
            return deltas;
        }


        List<Knowledge> kList = new ArrayList<>(Lists.transform(getDependencies(), know::get));
        Knowledge defKnow = know.get(getDefinedRelation());

        for (Tuple t : delta.getDisabledSet()) {
            long falses = kList.stream().filter(k -> k.isFalse(t)).count();
            if (falses == deps.size()) {
                // ... all deps are F now, so their union will be F as well (propagate upwards)
                defDelta.getDisabledSet().add(t);
                // TODO: If defKnow.isTrue(t), we would have a TF that could be propagated as well
            } else if (falses == deps.size() - 1 && defKnow.isStrictlyTrue(t)) {
                // ... all but one dep is F but the union is T, so the last remaining one dep must also be T
                for (int i = 0; i < kList.size(); i++) {
                    if (kList.get(i).isUnknown(t)) {
                        deltas.get(i).getEnabledSet().add(t);
                        break;
                    }
                }
            }
        }
        return deltas;
    }

    // This method propagates knowledge top-down
    @Override
    protected List<Knowledge.Delta> topDownKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        final List<Relation> deps = getDependencies();
        List<Knowledge.Delta> deltas = new ArrayList<>(deps.size() + 1);
        // --- Disabled sets ---
        for (int i = 0; i < deps.size(); i++) {
            // Disabled sets always propagate downwards to each dependency
            deltas.add(new Knowledge.Delta(delta.getDisabledSet(), new TupleSet()));
        }
        deltas.add(new Knowledge.Delta()); // The defined relation has no change

        // --- Enabled sets ---
        if (delta.getEnabledSet().isEmpty()) {
            return deltas;
        }

        List<Knowledge> kList = new ArrayList<>(Lists.transform(deps, know::get)); // We copy to avoid repeated lookups
        for (Tuple t : delta.getEnabledSet()) {
            // If 2+ relations have unknown knowledge ? about <t>, then we cannot propagate
            // If exactly 1 has entry ?, and all others are F, we can propagate T downwards
            for (int i = 0; i < kList.size(); i++) {
                if (!kList.get(i).isFalse(t)) {
                    if (kList.subList(i + 1, kList.size()).stream().allMatch(k -> k.isFalse(t))) {
                        // Exactly 1 relation has ?, all others are at least F
                        deltas.get(i).getEnabledSet().add(t);
                    } else {
                        // We have found 2 relations that are not false entries (? or T), so we cannot propagate
                        break;
                    }
                }
            }
        }
        return deltas;
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        Knowledge k = new Knowledge();
        for (Relation rel : getDependencies()) {
            Knowledge kRel = know.get(rel);
            k.getMaySet().addAll(kRel.getMaySet());
            k.getMustSet().addAll(kRel.getMustSet());
        }
        return k;
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know) {
        assert getDependencies().contains(changed);
        return delta; // Changes are propagated as they are for union
    }

    @Override
    public List<TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation, Knowledge> know) {
        return Lists.transform(getDependencies(), dep -> activeSet);
    }
}
