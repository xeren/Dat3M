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

// Example implementation of an n-ary intersection
public class Intersection extends DerivedDefinition {

    public Intersection(Relation definedRel, List<Relation> intersectRels) {
        super(definedRel, intersectRels);
    }

    public Intersection(Relation definedRel, Relation... dependencies) {
        this(definedRel, Arrays.asList(dependencies));
    }


    @Override
    protected String getOperationSymbol() { return " & "; }

    @Override
    protected List<Knowledge.Delta> bottomUpKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        final List<Relation> deps = getDependencies();
        List<Knowledge.Delta> deltas = new ArrayList<>(deps.size() + 1);
        for (int i = 0; i < deps.size(); i++) {
            deltas.add(new Knowledge.Delta());
        }
        // disabled sets always propagate upwards
        Knowledge.Delta defDelta = new Knowledge.Delta(delta.getDisabledSet(), new TupleSet());
        deltas.add(defDelta);

        if (delta.getEnabledSet().isEmpty()) {
            return deltas;
        }

        List<Knowledge> kList = new ArrayList<>(Lists.transform(getDependencies(), know::get));
        Knowledge defKnow = know.get(getDefinedRelation());

        for (Tuple t : delta.getEnabledSet()) {
            long trues = kList.stream().filter(k -> k.isTrue(t)).count();
            if (trues == deps.size()) {
                // ... all deps are T now, so their intersection will be T as well (propagate upwards)
                defDelta.getEnabledSet().add(t);
            } else if (trues == deps.size() - 1 && defKnow.isStrictlyFalse(t)) {
                // ... all but one dep is T but the intersection is F, so the last remaining one dep must be F
                for (int i = 0; i < kList.size(); i++) {
                    if (kList.get(i).isUnknown(t)) {
                        deltas.get(i).getDisabledSet().add(t);
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
        // --- Enabled sets ---
        for (int i = 0; i < deps.size(); i++) {
            // Enabled sets always propagate downwards to each dependency
            deltas.add(new Knowledge.Delta(new TupleSet(), delta.getEnabledSet()));
        }
        deltas.add(new Knowledge.Delta()); // The defined relation has no change

        // --- Disabled sets ---
        if (delta.getDisabledSet().isEmpty()) {
            return deltas;
        }

        List<Knowledge> kList = new ArrayList<>(Lists.transform(deps, know::get)); // We copy to avoid repeated lookups
        for (Tuple t : delta.getDisabledSet()) {
            // If 2+ relations have unknown knowledge ? about <t>, then we cannot propagate
            // If exactly 1 has entry ?, and all others are F, we can propagate T downwards
            for (int i = 0; i < kList.size(); i++) {
                if (!kList.get(i).isTrue(t)) {
                    if (kList.subList(i + 1, kList.size()).stream().allMatch(k -> k.isTrue(t))) {
                        // All but one is T, so the last one must be F
                        deltas.get(i).getDisabledSet().add(t);
                    } else {
                        // We have found 2 relations that are not T, so we cannot propagate F downwards
                        break;
                    }
                }
            }
        }
        return deltas;
    }


    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        List<Knowledge> kList = Lists.transform(getDependencies(), know::get);
        Knowledge k = kList.get(0).copy();
        for (Knowledge kRel : kList.subList(1, kList.size())) {
            k.getMaySet().removeIf(kRel::isFalse);
            k.getMustSet().removeIf(t -> !kRel.isTrue(t));
        }
        return k;
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know) {
        assert getDependencies().contains(changed);
        Knowledge.SetDelta intersectDelta = new Knowledge.SetDelta();
        List<Knowledge> kList = new ArrayList<>(Lists.transform(getDependencies(), know::get));

        // If the tuple is never F in any relation, then it is also not F in the intersection
        for (Tuple t : delta.getAddedMaySet()) {
            if (kList.stream().noneMatch(k -> k.isFalse(t))) {
                intersectDelta.getAddedMaySet().add(t);
            }
        }

        // If the tuple is T in all relations, then it is also T in the intersection
        for (Tuple t : delta.getAddedMustSet()) {
            if (kList.stream().allMatch(k -> k.isTrue(t))) {
                intersectDelta.getAddedMustSet().add(t);
            }
        }
        return intersectDelta;
    }

    @Override
    public List<TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation, Knowledge> know) {
        return Lists.transform(getDependencies(), dep -> activeSet);
    }

}
