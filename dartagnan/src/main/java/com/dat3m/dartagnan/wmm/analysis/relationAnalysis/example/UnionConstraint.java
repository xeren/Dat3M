package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.DerivedRelationConstraint;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Example implementation of an n-ary union
public class UnionConstraint extends DerivedRelationConstraint {

    public UnionConstraint(Relation definedRel, List<Relation> unionRels) {
        super(definedRel, unionRels);
    }

    @Override
    public List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        assert getConstrainedRelations().contains(changed);
        return changed == getDefinedRelation() ? topDownClosure(delta, know) : bottomUpClosure(delta, know);
    }

    private List<Knowledge.Delta> bottomUpClosure(Knowledge.Delta delta, Map<Relation, Knowledge> know) {
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
            long unknowns = kList.stream().filter(k -> k.isUnknown(t)).count();
            if (unknowns == 0) {
                // (1) If <changed(t)> was the only ?, we can propagate F upwards
                defDelta.getDisabledSet().add(t);
                // TODO: If defKnow.isTrue(t), we would have a TF that could be propagated as well
            } else if (unknowns == 1 && defKnow.isTrue(t)) {
                // (2) If there is a single r with ? and the defined rel has T, we can propagate T to r.
                for (int i = 0; i < kList.size(); i++) {
                    if (kList.get(i).isUnknown(t)) {
                        deltas.get(i).getEnabledSet().add(t);
                        break;
                    }
                }
            }
        }
        return null;
    }

    // This method propagates knowledge top-down
    private List<Knowledge.Delta> topDownClosure(Knowledge.Delta delta, Map<Relation, Knowledge> know) {
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
            // If exactly 1 has entry ?, then we can change it to T
            for (int i = 0; i < kList.size(); i++) {
                if (kList.get(i).getMaySet().contains(t)) {
                    if (kList.subList(i + 1, kList.size()).stream().noneMatch(k -> k.getMaySet().contains(t))) {
                        // Exactly 1 relation has ?
                        deltas.get(i).getEnabledSet().add(t);
                    } else {
                        // We have found 2 relations with ? entries, so we cannot propagate
                        break;
                    }
                }
            }
        }
        return null;
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
}
