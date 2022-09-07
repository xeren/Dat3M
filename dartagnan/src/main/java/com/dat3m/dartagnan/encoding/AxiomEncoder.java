package com.dat3m.dartagnan.encoding;

import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.wmm.Constraint;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.dat3m.dartagnan.wmm.utils.Utils;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dat3m.dartagnan.configuration.OptionNames.IDL_TO_SAT;
import static com.dat3m.dartagnan.encoding.ProgramEncoder.execution;
import static com.dat3m.dartagnan.wmm.utils.Utils.cycleVar;
import static com.dat3m.dartagnan.wmm.utils.Utils.edge;

@Options
final class AxiomEncoder implements Constraint.Visitor<BooleanFormula> {
    private final ExecutionAnalysis exec;
    private final SolverContext ctx;
    private final IntegerFormulaManager imgr;
    private final BooleanFormulaManager bmgr;

    @Option(
            name = IDL_TO_SAT,
            description = "Use SAT-based encoding for totality and acyclicity.",
            secure = true)
    private boolean useSATEncoding = false;

    AxiomEncoder(Context a, SolverContext c) {
        exec = a.requires(ExecutionAnalysis.class);
        ctx = c;
        imgr = ctx.getFormulaManager().getIntegerFormulaManager();
        bmgr = ctx.getFormulaManager().getBooleanFormulaManager();
    }

    @Override
    public BooleanFormula visitEmptiness(Axiom ax) {
        BooleanFormula enc = bmgr.makeTrue();
        Relation rel = ax.getRelation();
        for (Tuple tuple : rel.getEncodeTupleSet()) {
            enc = bmgr.and(enc, bmgr.not(rel.getSMTVar(tuple, ctx)));
        }
        return ax.isNegated() ? bmgr.not(enc) : enc;
    }

    @Override
    public BooleanFormula visitIrreflexivity(Axiom ax) {
        BooleanFormula enc = bmgr.makeTrue();
        Relation rel = ax.getRelation();
        for (Tuple tuple : rel.getEncodeTupleSet()) {
            if (tuple.isLoop()) {
                enc = bmgr.and(enc, bmgr.not(rel.getSMTVar(tuple, ctx)));
            }
        }
        return ax.isNegated() ? bmgr.not(enc) : enc;
    }

    @Override
    public BooleanFormula visitAcyclicity(Axiom ax) {
        Relation rel = ax.getRelation();
        String name = ax.getName();
        if (ax.isNegated()) {
            return inconsistentSAT(rel, name); // There is no IDL-based encoding for inconsistency
        } else {
            return useSATEncoding ? consistentSAT(rel, name) : consistentIDL(rel);
        }
    }

    @Override
    public BooleanFormula visitForceEncoding(Axiom ax) {
        return bmgr.makeTrue();
    }

    private BooleanFormula inconsistentSAT(Relation rel, String name) {
        final TupleSet encodeSet = rel.getEncodeTupleSet();
        BooleanFormula enc = bmgr.makeTrue();
        BooleanFormula eventsInCycle = bmgr.makeFalse();
        // We use Boolean variables which guess the edges and nodes constituting the cycle.
        for (Event e : encodeSet.stream().map(Tuple::getFirst).collect(Collectors.toSet())) {
            eventsInCycle = bmgr.or(eventsInCycle, cycleVar(rel.getName(), e, ctx));
            BooleanFormula in = encodeSet.getBySecond(e).stream().map(t -> getSMTCycleVar(t, name)).reduce(bmgr.makeFalse(), bmgr::or);
            BooleanFormula out = encodeSet.getByFirst(e).stream().map(t -> getSMTCycleVar(t, name)).reduce(bmgr.makeFalse(), bmgr::or);
            // We ensure that for every event in the cycle, there should be at least one incoming
            // edge and at least one outgoing edge that are also in the cycle.
            enc = bmgr.and(enc, bmgr.implication(cycleVar(rel.getName(), e, ctx), bmgr.and(in, out)));
            for (Tuple tuple : rel.getEncodeTupleSet()) {
                Event e1 = tuple.getFirst();
                Event e2 = tuple.getSecond();
                // If an edge is guessed to be in a cycle, the edge must belong to relation,
                // and both events must also be guessed to be on the cycle.
                enc = bmgr.and(enc, bmgr.implication(getSMTCycleVar(tuple, name),
                        bmgr.and(rel.getSMTVar(tuple, ctx), cycleVar(rel.getName(), e1, ctx), cycleVar(rel.getName(), e2, ctx))));
            }
        }
        // A cycle exists if there is an event in the cycle.
        enc = bmgr.and(enc, eventsInCycle);
        return enc;
    }

    private BooleanFormula consistentIDL(Relation rel) {
        final Set<Tuple> edgesToEncode = rel.getEncodeTupleSet();
        final String clockVarName = rel.getName();
        BooleanFormula enc = bmgr.makeTrue();
        for (Tuple tuple : edgesToEncode) {
            enc = bmgr.and(enc, bmgr.implication(rel.getSMTVar(tuple, ctx),
                    imgr.lessThan(
                            Utils.intVar(clockVarName, tuple.getFirst(), ctx),
                            Utils.intVar(clockVarName, tuple.getSecond(), ctx))));
        }
        return enc;
    }

    private BooleanFormula consistentSAT(Relation rel, String name) {
        // We use a vertex-elimination graph based encoding.
        final Set<Tuple> edgeSet = rel.getEncodeTupleSet();
        // Build original graph G
        Map<Event, Set<Tuple>> inEdges = new HashMap<>();
        Map<Event, Set<Tuple>> outEdges = new HashMap<>();
        Set<Event> nodes = new HashSet<>();
        Set<Event> selfloops = new HashSet<>();         // Special treatment for self-loops
        for (final Tuple t : edgeSet) {
            final Event e1 = t.getFirst();
            final Event e2 = t.getSecond();
            if (t.isLoop()) {
                selfloops.add(e1);
            } else {
                nodes.add(e1);
                nodes.add(e2);
                outEdges.computeIfAbsent(e1, key -> new HashSet<>()).add(t);
                inEdges.computeIfAbsent(e2, key -> new HashSet<>()).add(t);
            }
        }
        // Handle corner-cases where some node has no ingoing or outgoing edges
        for (Event node : nodes) {
            outEdges.putIfAbsent(node, new HashSet<>());
            inEdges.putIfAbsent(node, new HashSet<>());
        }
        // Build vertex elimination graph G*, by iteratively modifying G
        Map<Event, Set<Tuple>> vertEleInEdges = new HashMap<>();
        Map<Event, Set<Tuple>> vertEleOutEdges = new HashMap<>();
        for (Event e : nodes) {
            vertEleInEdges.put(e, new HashSet<>(inEdges.get(e)));
            vertEleOutEdges.put(e, new HashSet<>(outEdges.get(e)));
        }
        List<Event[]> triangles = new ArrayList<>();
        // Build variable elimination ordering
        List<Event> varOrderings = new ArrayList<>(); // We should order this
        while (!nodes.isEmpty()) {
            // Find best vertex e to eliminate
            final Comparator<Event> comparator = Comparator.comparingInt(ev -> vertEleInEdges.get(ev).size() * vertEleOutEdges.get(ev).size());
            final Event e = nodes.stream().min(comparator).get();
            varOrderings.add(e);
            // Eliminate e
            nodes.remove(e);
            final Set<Tuple> in = inEdges.remove(e);
            final Set<Tuple> out = outEdges.remove(e);
            in.forEach(t -> outEdges.get(t.getFirst()).remove(t));
            out.forEach(t -> inEdges.get(t.getSecond()).remove(t));
            // Create new edges due to elimination of e
            for (Tuple t1 : in) {
                Event e1 = t1.getFirst();
                for (Tuple t2 : out) {
                    Event e2 = t2.getSecond();
                    if (e2 == e1 || exec.areMutuallyExclusive(e1, e2)) {
                        continue;
                    }
                    Tuple t = new Tuple(e1, e2);
                    // Update next graph in the elimination sequence
                    inEdges.get(e2).add(t);
                    outEdges.get(e1).add(t);
                    // Update vertex elimination graph
                    vertEleOutEdges.get(e1).add(t);
                    vertEleInEdges.get(e2).add(t);
                    // Store constructed triangle
                    triangles.add(new Event[]{e1, e, e2});
                }
            }
        }
        // --- Create encoding ---
        final Set<Tuple> minSet = rel.getMinTupleSet();
        BooleanFormula enc = bmgr.makeTrue();
        // Basic lifting
        for (Tuple t : edgeSet) {
            BooleanFormula cond = minSet.contains(t) ? execution(t.getFirst(), t.getSecond(), exec, ctx) : rel.getSMTVar(t, ctx);
            enc = bmgr.and(enc, bmgr.implication(cond, getSMTCycleVar(t, name)));
        }
        // Encode triangle rules
        for (Event[] tri : triangles) {
            Tuple t1 = new Tuple(tri[0], tri[1]);
            Tuple t2 = new Tuple(tri[1], tri[2]);
            Tuple t3 = new Tuple(tri[0], tri[2]);
            BooleanFormula cond = minSet.contains(t3) ?
                    execution(t3.getFirst(), t3.getSecond(), exec, ctx)
                    : bmgr.and(getSMTCycleVar(t1, name), getSMTCycleVar(t2, name));
            enc = bmgr.and(enc, bmgr.implication(cond, getSMTCycleVar(t3, name)));
        }
        //  --- Encode inconsistent assignments ---
        // Handle self-loops
        for (Event e : selfloops) {
            enc = bmgr.and(enc, bmgr.not(rel.getSMTVar(e, e, ctx)));
        }
        // Handle remaining cycles
        for (int i = 0; i < varOrderings.size(); i++) {
            Set<Tuple> out = vertEleOutEdges.get(varOrderings.get(i));
            for (Tuple t : out) {
                if (varOrderings.indexOf(t.getSecond()) > i && vertEleInEdges.get(t.getSecond()).contains(t)) {
                    BooleanFormula cond = minSet.contains(t) ? bmgr.makeTrue() : getSMTCycleVar(t, name);
                    enc = bmgr.and(enc, bmgr.implication(cond, bmgr.not(getSMTCycleVar(t.getInverse(), name))));
                }
            }
        }
        return enc;
    }

    private BooleanFormula getSMTCycleVar(Tuple edge, String name) {
        return edge(name + "-cycle", edge.getFirst(), edge.getSecond(), ctx);
    }
}
