package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.AbstractConstraint;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Axiom;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.Sets;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Assumption extends AbstractConstraint implements Axiom {

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
        Knowledge kRel = know.get(relation);
        // This should ideally be empty if the knowledge propagation worked as expected
        return Collections.singletonList(new TupleSet(Sets.filter(Sets.union(enabled, disabled), kRel::isUnknown)));
    }

    @Override
    public BooleanFormula encodeAxiom(Map<Relation, Knowledge> know, EncodingContext ctx) {
        Knowledge kRel = know.get(relation);
        BooleanFormulaManager bmgr = ctx.getBmgr();
        BooleanFormula enc = bmgr.makeTrue();

        for (Tuple t : Sets.difference(enabled, kRel.getMustSet())) {
            enc = bmgr.and(enc, bmgr.equivalence(relation.getSMTVar(t, kRel, ctx), ctx.execPair(t)));
        }
        for (Tuple t : Sets.intersection(disabled, kRel.getMaySet())) {
            enc = bmgr.and(enc, bmgr.implication(relation.getSMTVar(t, kRel, ctx), bmgr.makeFalse()));
        }

        return enc;
    }
}
