package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.DerivedDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

import java.util.*;

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
    protected Map<Relation,Knowledge.Delta> bottomUpKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        // enabled sets always propagate upwards
        Knowledge.Delta defDelta = new Knowledge.Delta(new TupleSet(), delta.getEnabledSet());
        if (delta.getDisabledSet().isEmpty()) {
            return Map.of(definedRelation, defDelta);
        }

        final List<Relation> deps = dependencies;
        Map<Relation,Knowledge.Delta> deltas = new HashMap<>();
        for (Relation r : deps) {
            deltas.put(r, new Knowledge.Delta());
        }
        deltas.put(definedRelation, defDelta);

        List<Knowledge> kList = new ArrayList<>(Lists.transform(dependencies, know::get));
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
                        deltas.get(deps.get(i)).getEnabledSet().add(t);
                        break;
                    }
                }
            }
        }
        return deltas;
    }

    // This method propagates knowledge top-down
    @Override
    protected Map<Relation,Knowledge.Delta> topDownKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {

        final List<Relation> deps = dependencies;
        Map<Relation,Knowledge.Delta> deltas = new HashMap<>();
        // --- Disabled sets ---
        if (!delta.getDisabledSet().isEmpty()) {
            for (Relation r : deps) {
                // Disabled sets always propagate downwards to each dependency
                deltas.put(r, new Knowledge.Delta(delta.getDisabledSet(), new TupleSet()));
            }
        }

        List<Knowledge> kList = new ArrayList<>(Lists.transform(deps, know::get)); // We copy to avoid repeated lookups
        // --- Enabled sets ---
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
        for (Relation rel : dependencies) {
            Knowledge kRel = know.get(rel);
            k.getMaySet().addAll(kRel.getMaySet());
            k.getMustSet().addAll(kRel.getMustSet());
        }
        return k;
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know) {
        assert dependencies.contains(changed);
        return delta; // Changes are propagated as they are for union
    }

    @Override
    public Map<Relation,TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation, Knowledge> know) {
        return Maps.asMap(Set.copyOf(dependencies), dep -> activeSet);
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation, Knowledge> know, EncodingContext ctx) {
        Knowledge kRel = know.get(definedRelation);
        List<Knowledge> kDeps = new ArrayList<>(Lists.transform(dependencies, know::get));
        BooleanFormulaManager bmgr = ctx.getBmgr();
        BooleanFormula enc = bmgr.makeTrue();

        for (Tuple t : toBeEncoded) {
            BooleanFormula union = bmgr.makeFalse();
            for (int i = 0; i < dependencies.size(); i++) {
                union = bmgr.or(union, dependencies.get(i).getSMTVar(t, kDeps.get(i), ctx));
            }
            enc = bmgr.and(enc, bmgr.equivalence(getSMTVar(t, kRel, ctx), union));
        }

        return enc;
    }
}
