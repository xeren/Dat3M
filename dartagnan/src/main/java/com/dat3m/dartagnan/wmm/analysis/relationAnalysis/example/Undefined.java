package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example;

import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.BaseDefinition;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/*
    The relation is unconstrained and can take any values.
    NOTE: Currently, it is restricted to only visible events.
 */
public class Undefined extends BaseDefinition {

    public Undefined(Relation baseRel) {
        super(baseRel);
    }

    @Override
    public String getTerm() {
        return "__undef";
    }

    @Override
    public List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        return Collections.singletonList(new Knowledge.Delta());
    }

    @Override
    public List<TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        return Collections.emptyList();
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        List<Event> visibleEvents = task.getProgram().getCache().getEvents(FilterBasic.get(Tag.VISIBLE));
        TupleSet maySet = new TupleSet();
        for (Event e1 : visibleEvents) {
            for (Event e2 : visibleEvents) {
                maySet.add(new Tuple(e1, e2));
            }
        }
        return new Knowledge(maySet, new TupleSet());
    }
}
