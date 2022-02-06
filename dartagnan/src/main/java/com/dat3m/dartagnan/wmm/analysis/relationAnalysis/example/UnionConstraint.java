package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.DefiningConstraint;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Example implementation of an n-ary union
public class UnionConstraint implements DefiningConstraint {

    private final List<Relation> rels;

    public UnionConstraint(List<Relation> rels, Relation unionRel) {
        this.rels = new ArrayList<>(rels);
        rels.add(unionRel);
    }

    @Override
    public List<Relation> getConstrainedRelations() {
        return rels;
    }

    //TODO: Somewhat messy
    @Override
    public List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        assert rels.contains(changed);

        List<Knowledge.Delta> deltas = new ArrayList<>(rels.size());
        if (changed == getDefinedRelation()) {
            // --------- Top-down ---------
            for (int i = 0; i < rels.size() - 1; i++) {
                // Disabled sets always propagate downwards
                deltas.add(new Knowledge.Delta(delta.getDisabledSet(), new TupleSet()));
            }
            deltas.add(new Knowledge.Delta()); // The final relation has no change

            if (delta.getEnabledSet().isEmpty()) {
                return deltas;
            }

            List<Knowledge> kList = new ArrayList<>(rels.size() - 1);
            for (int i = 0; i < rels.size() - 1; i++) {
                kList.add(know.get(rels.get(i)));
            }

            for (Tuple t : delta.getEnabledSet()) {
                for (int i = 0; i < kList.size(); i++) {
                    if (kList.get(i).getMaySet().contains(t)) {
                        if (kList.subList(i + 1, kList.size()).stream().noneMatch(k -> k.getMaySet().contains(t))) {
                            deltas.get(i).getEnabledSet().add(t);
                        } else {
                            // We have found 2 relations with ? entries, so we cannot propagate that tuple
                            break;
                        }
                    }
                }
            }
        } else {
            // ---------- Bottom-up ------------
            for (int i = 0; i < rels.size() - 1; i++) {
                deltas.add(new Knowledge.Delta());
            }
            // must-sets always propagate upwards
            Knowledge.Delta defDelta = new Knowledge.Delta(new TupleSet(), delta.getEnabledSet());
            deltas.add(defDelta);

            if (delta.getDisabledSet().isEmpty()) {
                return deltas;
            }

            List<Knowledge> kList = new ArrayList<>(rels.size() - 1);
            for (int i = 0; i < rels.size() - 1; i++) {
                kList.add(know.get(rels.get(i)));
            }
            Knowledge defK = know.get(getDefinedRelation());

            for (Tuple t : delta.getDisabledSet()) {
                long unknowns = kList.stream().filter(k -> k.isUnknown(t)).count();
                if (unknowns == 0) {
                    // (1) If <changed(t)> was the only ?, we can propagate F upwards
                    defDelta.getDisabledSet().add(t);
                    // TODO: If defk.isTrue(t), we would have a TF that could be propagated as well
                } else if (unknowns == 1 && defK.isTrue(t)) {
                    // (2) If there is a single r with ? and the defined rel has T, we can propagate F to r.
                    for (int i = 0; i < kList.size(); i++) {
                        if (kList.get(i).isUnknown(t)) {
                            deltas.get(i).getDisabledSet().add(t);
                            break;
                        }
                    }
                }
            }
        }

        return deltas;
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        Knowledge k = new Knowledge();
        for (Relation rel : rels.subList(0, rels.size() - 1)) {
            Knowledge kRel = know.get(rel);
            k.getMaySet().addAll(kRel.getMaySet());
            k.getMustSet().addAll(kRel.getMustSet());
        }
        return k;
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know) {
        assert rels.contains(changed);
        return delta;
    }
}
