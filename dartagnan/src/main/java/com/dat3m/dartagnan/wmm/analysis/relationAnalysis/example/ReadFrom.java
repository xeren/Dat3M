package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example;

import com.dat3m.dartagnan.program.analysis.AliasAnalysis;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.BaseDefinition;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.relation.RelationNameRepository;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.dat3m.dartagnan.program.event.Tag.READ;
import static com.dat3m.dartagnan.program.event.Tag.WRITE;

public class ReadFrom extends BaseDefinition {

    public ReadFrom(Relation baseRel) {
        super(baseRel);
    }

    @Override
    public String getTerm() {
        return "__" + RelationNameRepository.RF;
    }

    @Override
    public List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        return Collections.emptyList();
        // One could check if there is only a single possible rf-edge remaining and set it to T.
        // Or if some rf-edge is T, then one can disable other read froms
    }

    @Override
    public List<TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        return Collections.singletonList(know.get(baseRel).getMaySet());
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        AliasAnalysis alias = analysisContext.get(AliasAnalysis.class);
        TupleSet maxTupleSet = new TupleSet();
        TupleSet minTupleSet = new TupleSet();

        List<Event> loadEvents = task.getProgram().getCache().getEvents(FilterBasic.get(READ));
        List<Event> storeEvents = task.getProgram().getCache().getEvents(FilterBasic.get(WRITE));

        for(Event e1 : storeEvents){
            for(Event e2 : loadEvents){
                if(alias.mayAlias((MemEvent) e1, (MemEvent) e2)){
                    maxTupleSet.add(new Tuple(e1, e2));
                }
            }
        }

        return new Knowledge(maxTupleSet, minTupleSet);
    }
}
