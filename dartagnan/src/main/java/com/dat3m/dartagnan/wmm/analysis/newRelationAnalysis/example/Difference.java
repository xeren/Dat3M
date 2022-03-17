package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.DerivedDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;

public final class Difference extends DerivedDefinition {

    public Difference(Relation r, Relation minuend, Relation subtrahend) {
        super(r, List.of(minuend,subtrahend));
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        Knowledge minuend = know.get(dependencies.get(0));
        Knowledge subtrahend = know.get(dependencies.get(1));
        TupleSet may = new TupleSet(difference(minuend.getMaySet(),subtrahend.getMustSet()));
        TupleSet must = new TupleSet(difference(minuend.getMustSet(),subtrahend.getMaySet()));
        return new Knowledge(may,must);
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation, Knowledge> know) {
        verify(changed == dependencies.get(0), "unstratified model");
        Knowledge subtrahend = know.get(dependencies.get(1));
        TupleSet may = new TupleSet(difference(delta.getAddedMaySet(),subtrahend.getMustSet()));
        TupleSet must = new TupleSet(difference(delta.getAddedMustSet(),subtrahend.getMaySet()));
        return new Knowledge.SetDelta(may,must);
    }

    @Override
    public Map<Relation, TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation, Knowledge> know) {
        Relation subtrahend = dependencies.get(1);
        return Map.of(
            dependencies.get(0),activeSet,
            subtrahend,new TupleSet(intersection(activeSet,know.get(subtrahend).getMaySet())));
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation, Knowledge> know, EncodingContext ctx) {
        BooleanFormulaManager bmgr = ctx.getBmgr();
        Relation minuend = dependencies.get(0);
        Relation subtrahend = dependencies.get(1);
        Knowledge knowledgeThis = know.get(definedRelation);
        Knowledge knowledgeMinuend = know.get(minuend);
        Knowledge knowledgeSubtrahend = know.get(subtrahend);
        BooleanFormula enc = bmgr.makeTrue();
        for(Tuple tuple : toBeEncoded) {
            enc = bmgr.and(enc,bmgr.equivalence(getSMTVar(tuple,knowledgeThis,ctx),bmgr.and(
                    minuend.getSMTVar(tuple,knowledgeMinuend,ctx),
                    bmgr.not(subtrahend.getSMTVar(tuple,knowledgeSubtrahend,ctx)))));
        }
        return enc;
    }

    @Override
    protected String getOperationSymbol() {
        return " \\ ";
    }

    @Override
    protected Map<Relation, Knowledge.Delta> bottomUpKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        Relation minuend = dependencies.get(0);
        Relation subtrahend = dependencies.get(1);
        Knowledge knowledgeThis = know.get(definedRelation);
        int index = dependencies.indexOf(changed);
        verify(index == 0 || index == 1);
        if(index == 0) {
            Knowledge knowledgeSubstrahend = know.get(subtrahend);
            return Map.of(
                    definedRelation,new Knowledge.Delta(
                            delta.getDisabledSet(),
                            new TupleSet(difference(delta.getEnabledSet(),knowledgeSubstrahend.getMaySet()))),
                    subtrahend,new Knowledge.Delta(
                            new TupleSet(),
                            new TupleSet(difference(delta.getEnabledSet(),knowledgeThis.getMaySet()))));
        }
        Knowledge knowledgeMinuend = know.get(minuend);
        return Map.of(
                definedRelation,new Knowledge.Delta(
                        delta.getEnabledSet(),
                        new TupleSet(intersection(delta.getDisabledSet(),knowledgeMinuend.getMustSet()))),
                minuend,new Knowledge.Delta(
                        new TupleSet(difference(delta.getDisabledSet(),knowledgeThis.getMaySet())),
                        new TupleSet(intersection(delta.getDisabledSet(),knowledgeThis.getMustSet()))));
    }

    @Override
    protected Map<Relation, Knowledge.Delta> topDownKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        verify(changed == definedRelation);
        Relation minuend = dependencies.get(0);
        Relation subtrahend = dependencies.get(1);
        return Map.of(
                minuend,new Knowledge.Delta(
                        new TupleSet(difference(delta.getDisabledSet(),know.get(subtrahend).getMaySet())),
                        delta.getEnabledSet()),
                subtrahend,new Knowledge.Delta(
                        delta.getEnabledSet(),
                        new TupleSet(intersection(delta.getDisabledSet(),know.get(minuend).getMustSet()))));
    }
}
