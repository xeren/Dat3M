package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.CATAxiom;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.Map;

import static java.util.stream.Collectors.toCollection;

public class Irreflexive extends CATAxiom {

    public Irreflexive(Relation r) {
        super(r);
    }

    @Override
    public Map<Relation,Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation,Knowledge> know) {
        return Map.of(rel,new Knowledge.Delta(
                know.get(rel).getMaySet().stream().filter(Tuple::isLoop).collect(toCollection(TupleSet::new)),
                new TupleSet()));
    }

    @Override
    public Map<Relation,Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation,Knowledge> know) {
        return Map.of();
    }

    @Override
    public Map<Relation,TupleSet> computeActiveSets(Map<Relation,Knowledge> know) {
        return Map.of();
    }

    @Override
    public BooleanFormula encodeAxiom(Map<Relation,Knowledge> know, EncodingContext ctx) {
        return ctx.getBmgr().makeTrue();
    }
}
