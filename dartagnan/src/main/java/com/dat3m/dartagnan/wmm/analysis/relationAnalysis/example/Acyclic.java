package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example;

import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.AxiomaticConstraint;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Acyclic extends AxiomaticConstraint {

    private TupleSet transitiveMinSet;

    public Acyclic(Relation rel) { super(rel);}

    @Override
    public List<Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> know) {
        Knowledge k = know.get(rel);

        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        TupleSet minSet = k.getMustSet();

        // (1) Approximate transitive closure of minSet (only gets computed when crossEdges are available)
        List<Tuple> crossEdges = minSet.stream()
                .filter(t -> t.isCrossThread() && !t.getFirst().is(Tag.INIT))
                .collect(Collectors.toList());
        TupleSet transMinSet = crossEdges.isEmpty() ? minSet : new TupleSet(minSet);
        for (Tuple crossEdge : crossEdges) {
            Event e1 = crossEdge.getFirst();
            Event e2 = crossEdge.getSecond();

            List<Event> ingoing = new ArrayList<>();
            ingoing.add(e1); // ingoing events + self
            minSet.getBySecond(e1).stream().map(Tuple::getFirst)
                    .filter(e -> exec.isImplied(e, e1))
                    .forEach(ingoing::add);


            List<Event> outgoing = new ArrayList<>();
            outgoing.add(e2); // outgoing edges + self
            minSet.getByFirst(e2).stream().map(Tuple::getSecond)
                    .filter(e -> exec.isImplied(e, e2))
                    .forEach(outgoing::add);

            for (Event in : ingoing) {
                for (Event out : outgoing) {
                    transMinSet.add(new Tuple(in, out));
                }
            }
        }

        // Disable all edges opposing the transitive min set
        this.transitiveMinSet = transMinSet;
        return Collections.singletonList(new Knowledge.Delta(transitiveMinSet.inverse(), new TupleSet()));
    }

    @Override
    public List<Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        assert  changed == getConstrainedRelation();
        Knowledge.Delta newDelta = new Knowledge.Delta();
        if (delta.getEnabledSet().isEmpty()) {
            // We can only derive new knowledge from added edges, so if we have none, we return early
            return Collections.singletonList(newDelta);
        }

        for (Tuple t : delta.getEnabledSet()) {
            if (transitiveMinSet.add(t)) {
                newDelta.getDisabledSet().add(t.getInverse());
            }
        }
        //TODO: we should transitively close <transitiveMinSet>
        return Collections.singletonList(newDelta);
    }
}
