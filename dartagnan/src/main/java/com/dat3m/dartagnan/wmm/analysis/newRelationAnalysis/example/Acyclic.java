package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.CATAxiom;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;

import java.util.*;
import java.util.stream.Collectors;

public class Acyclic extends CATAxiom {

    private TupleSet transitiveMinSet;
    private TupleSet activeSet;

    public Acyclic(Relation rel) { super(rel);}

    @Override
    public Map<Relation,Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> know) {
        Knowledge k = know.get(rel);

        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        TupleSet minSet = k.getMustSet();

        // (1) Approximate transitive closure of minSet (only gets computed when crossEdges are available)
        List<Tuple> crossEdges = minSet.stream()
                .filter(t -> t.isCrossThread() && !t.getFirst().is(Tag.INIT))
                .collect(Collectors.toList());
        TupleSet transMinSet = new TupleSet(minSet);
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
        Knowledge.Delta delta = new Knowledge.Delta(new TupleSet(transitiveMinSet.inverse()), new TupleSet());
        // Disable the diagonal
        delta.getDisabledSet().addAll(
                Lists.transform(task.getProgram().getCache().getEvents(FilterBasic.get(Tag.VISIBLE)), e -> new Tuple(e, e))
        );
        return Map.of(rel,delta);
    }

    @Override
    public Map<Relation,Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        assert changed == getConstrainedRelation();
        if (delta.getEnabledSet().isEmpty()) {
            // We can only derive new knowledge from added edges, so if we have none, we return early
            return Map.of();
        }

        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);

        Knowledge.Delta newDelta = new Knowledge.Delta();
        for (Tuple t : delta.getEnabledSet()) {
            if (transitiveMinSet.add(t)) {
                newDelta.getDisabledSet().add(t.getInverse());
            }
            Event x = t.getFirst();
            Event y = t.getSecond();
            boolean implies = exec.isImplied(x, y);
            boolean impliedBy = exec.isImplied(y, x);
            for (Tuple s : transitiveMinSet.getBySecond(x)) {
                Event w = s.getFirst();
                if ((impliedBy || exec.isImplied(w, x)) && transitiveMinSet.add(new Tuple(w, y))) {
                    newDelta.getDisabledSet().add(new Tuple(y, w));
                }
            }
            for (Tuple u : transitiveMinSet.getByFirst(y)) {
                Event z = u.getSecond();
                if ((implies || exec.isImplied(z, y)) && transitiveMinSet.add(new Tuple(x, z))) {
                    newDelta.getDisabledSet().add(new Tuple(z, x));
                }
            }
        }

        return Map.of(rel,newDelta);
    }

    @Override
    public Map<Relation,TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        // ====== Construct [Event -> Successor] mapping ======
        Map<Event, Collection<Event>> succMap = new HashMap<>();
        TupleSet relMay = know.get(rel).getMaySet();
        for (Tuple t : relMay) {
            succMap.computeIfAbsent(t.getFirst(), key -> new ArrayList<>()).add(t.getSecond());
        }

        // ====== Compute SCCs ======
        DependencyGraph<Event> depGraph = DependencyGraph.from(succMap.keySet(), succMap);
        TupleSet activeSet = new TupleSet();
        for (Set<DependencyGraph<Event>.Node> scc : depGraph.getSCCs()) {
            for (DependencyGraph<Event>.Node node1 : scc) {
                for (DependencyGraph<Event>.Node node2 : scc) {
                    Tuple t = new Tuple(node1.getContent(), node2.getContent());
                    if (relMay.contains(t)) {
                        activeSet.add(t);
                    }
                }
            }
        }

        //TODO: Recompute <transitiveMinSet> as it may be outdated
        // and worse yet, it may be actually wrong (though this should not happen)
        TupleSet reduct = TupleSet.approximateTransitiveMustReduction(
                analysisContext.get(ExecutionAnalysis.class), transitiveMinSet);
        // Remove (must(r)+ \ reduct(must(r)+)
        activeSet.removeAll(Sets.difference(transitiveMinSet, reduct));

        this.activeSet = activeSet;
        return Map.of(rel,activeSet);
    }

    @Override
    public BooleanFormula encodeAxiom(Map<Relation, Knowledge> know, EncodingContext ctx) {
        Knowledge kRel = know.get(rel);

        BooleanFormulaManager bmgr = ctx.getBmgr();
        IntegerFormulaManager imgr = ctx.getImgr();

        BooleanFormula enc = bmgr.makeTrue();
        for(Tuple tuple : activeSet) {
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();
            enc = bmgr.and(enc, bmgr.implication(rel.getSMTVar(tuple, kRel, ctx),
                    imgr.lessThan(ctx.clockVar(rel.getNameOrTerm(), e1), ctx.clockVar(rel.getNameOrTerm(), e2))));
        }
        return enc;
    }
}
