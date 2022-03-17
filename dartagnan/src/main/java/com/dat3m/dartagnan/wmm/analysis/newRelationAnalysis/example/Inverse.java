package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.DerivedDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Verify.verify;

public final class Inverse extends DerivedDefinition {

    public Inverse(Relation r, Relation c) {
        super(r, List.of(c));
    }

    @Override
    public BooleanFormula getSMTVar(Tuple t, Knowledge kRel, EncodingContext ctx) {
        return dependencies.get(0).getSMTVar(t.getInverse(), kRel, ctx);
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        Knowledge k = know.get(dependencies.get(0));
        return new Knowledge(k.getMaySet().inverse(),k.getMustSet().inverse());
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know) {
        verify(changed == dependencies.get(0));
        return new Knowledge.SetDelta(delta.getAddedMaySet().inverse(),delta.getAddedMustSet().inverse());
    }

    @Override
    public Map<Relation, TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation, Knowledge> know) {
        return Map.of(dependencies.get(0),activeSet.inverse());
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation, Knowledge> know, EncodingContext ctx) {
        return ctx.getBmgr().makeTrue();
    }

    @Override
    protected String getOperationSymbol() {
        return "^-1";
    }

    @Override
    protected Map<Relation, Knowledge.Delta> bottomUpKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        return Map.of(definedRelation,new Knowledge.Delta(delta.getDisabledSet().inverse(),delta.getEnabledSet().inverse()));
    }

    @Override
    protected Map<Relation, Knowledge.Delta> topDownKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        return Map.of(dependencies.get(0),new Knowledge.Delta(delta.getDisabledSet().inverse(),delta.getEnabledSet().inverse()));
    }
}
