package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.BaseDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.Map;

public class SetIdentity extends BaseDefinition {

    private final FilterAbstract filter;

    public SetIdentity(Relation r, FilterAbstract f) {
        super(r);
        filter = f;
    }

    // --------------------------  Definition        --------------------------

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation,Knowledge> know) {
        TupleSet result = new TupleSet();
        for(Event e : task.getProgram().getCache().getEvents(filter)) {
            result.add(new Tuple(e,e));
        }
        return new Knowledge(result,result);
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation,Knowledge> know, EncodingContext ctx) {
        return ctx.getBmgr().makeTrue();
    }

    @Override
    public String getTerm() {
        return "[" + filter + "]";
    }

    // --------------------------  Constraint        --------------------------

    @Override
    public Map<Relation,Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation,Knowledge> know) {
        return Map.of();
    }

    @Override
    public Map<Relation,TupleSet> computeActiveSets(Map<Relation,Knowledge> know) {
        return Map.of();
    }
}
