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

import java.util.List;
import java.util.Map;

public final class Cartesian extends BaseDefinition {

    private final FilterAbstract first;
    private final FilterAbstract second;

    public Cartesian(Relation r, FilterAbstract x, FilterAbstract y) {
        super(r);
        first = x;
        second = y;
    }

    //Definition--------------------------------------------------------------------------------------------------------

    @Override
    public Map<Relation, Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        return null;
    }

    @Override
    public Map<Relation, TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        return null;
    }

    @Override
    public String getTerm() {
        return first + "*" + second;
    }

    //Constraint--------------------------------------------------------------------------------------------------------

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        TupleSet result = new TupleSet();
        List<Event> events = task.getProgram().getCache().getEvents(second);
        for(Event x : task.getProgram().getCache().getEvents(first)) {
            for(Event y : events) {
                result.add(new Tuple(x,y));
            }
        }
        return new Knowledge(result,result);
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation, Knowledge> know, EncodingContext ctx) {
        return ctx.getBmgr().makeTrue();
    }
}
