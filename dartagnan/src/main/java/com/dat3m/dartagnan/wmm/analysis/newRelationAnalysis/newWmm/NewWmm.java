package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.NewRelationAnalysis;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.RelationAnalysisResult;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example.*;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.base.Preconditions;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.*;

public class NewWmm {

    // ============================== Properties ==============================

    private final Set<Axiom> axioms = new HashSet<>();
    private final Set<Relation> relations = new HashSet<>();
    private final Map<String, Relation> name2RelMap = new HashMap<>();

    private int nextId = 0;

    public Set<Relation> getRelations() { return relations; }
    public Set<Axiom> getAxiomaticConstraints() { return axioms; }

    public Relation getRelationByNameOrTerm(String nameOrTerm) {
        if (name2RelMap.containsKey(nameOrTerm)) {
            return name2RelMap.get(nameOrTerm);
        }
        return relations.stream().filter(rel -> rel.getTerm().equals(nameOrTerm)).findFirst().orElse(null);
    }

    // =========================================================================

    // ============================== Construction ==============================

    private NewWmm() { }

    public static NewWmm createEmptyWmm() {
        return new NewWmm();
    }

    public static NewWmm createAnarchicWmm() {
        NewWmm wmm = createEmptyWmm();
        // Set up the basic anarchic relations (rf, co, idd etc.)
        // TODO: Add definitions for co, idd, addrDirect etc.
        wmm.addDefinition(new ReadFrom(wmm.newRelationWithName("rf")));
        wmm.addDefinition(new MemoryOrder(wmm.newRelationWithName("co"))); // Technically not part of the anarchic semantics
        wmm.newRelationWithName("idd");
        wmm.newRelationWithName("addrDirect");


        return wmm;
    }

    // -------------------------------------------------------------------------

    public Relation newRelation() {
        Relation rel = new Relation(this , nextId++);
        this.relations.add(rel);
        return rel;
    }

    public Relation newRelationWithName(String name) {
        Preconditions.checkState(!name2RelMap.containsKey(name), "A relation with name %s already exists", name);
        Relation newRel = newRelation();
        updateRelationName(newRel, name);
        return newRel;
    }

    // Deletes a relation from this WMM and all constraints associated with it
    // WARNING: This causes relations that were dependent on the deleted relation to become "undefined".
    public boolean deleteRelation(Relation rel) {
        if (relations.remove(rel)) {
            name2RelMap.remove(rel.getNameOrTerm());
            axioms.removeIf(ax -> ax.getConstrainedRelations().contains(rel));

            for (Relation r : relations) {
                if (r.getDependencies().contains(rel)) {
                    r.setDefinition(new Undefined(r));
                }
            }

            return true;
        }
        return false;
    }

    public boolean updateRelationName(Relation rel, String newName) {
        if (!relations.contains(rel) || name2RelMap.containsKey(newName)) {
            return false;
        }
        name2RelMap.remove(rel.getNameOrTerm());
        rel.setName(newName);
        if (newName != null) {
            name2RelMap.put(newName, rel);
        }

        return true;
    }

    // The added Definition overwrites the previous definition of its defined relation.
    public Definition addDefinition(Definition definition) {
        Preconditions.checkArgument(relations.containsAll(definition.getConstrainedRelations()),
                "The defining constraint refers to relations that are not part of this Wmm");
        Relation definedRel = definition.getDefinedRelation();
        Definition oldDefinition = definedRel.getDefinition();
        definedRel.setDefinition(definition);

        return oldDefinition;
    }

    public boolean addAxiom(Axiom axiom) {
        Preconditions.checkArgument(!(axiom instanceof Definition), "The provided axiom is also a definition." +
                "Use addDefinition instead.");
        Preconditions.checkArgument(relations.containsAll(axiom.getConstrainedRelations()),
                "The axiomatic constraint refers to relations that are not part of this Wmm");
        return axioms.add(axiom);
    }

    public boolean removeAxiom(Axiom axiom) {
        return axioms.remove(axiom);
    }

    // =========================================================================

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        DependencyGraph<Relation> depGraph = DependencyGraph.from(relations);
        for (Relation rel : depGraph.getNodeContents()) {
            builder.append(rel).append("\n");
        }

        for (Axiom ax : axioms) {
            builder.append(ax).append("\n");
        }

        return builder.toString();
    }


    // ============================== Testing code ==============================

    //TODO: This code should most likely go into the WmmEncoder class.
    //TODO 2: If we never need the active sets outside of encoding, we could also
    // make this method invoke the active set computation.
    public BooleanFormula encode(RelationAnalysisResult relAnalysis, EncodingContext ctx) {
        Preconditions.checkArgument(relAnalysis.hasActiveSets(),
                "No active sets have been computed. Encoding is impossible");
        Map<Relation, Knowledge> knowledgeMap = relAnalysis.getKnowledgeMap();
        Map<Relation, TupleSet> activeSets = relAnalysis.getActiveSetsMap();

        BooleanFormulaManager bmgr = ctx.getBmgr();
        BooleanFormula enc = bmgr.makeTrue();

        // Maybe this should be done in topological order, although it shouldn't matter, actually.
        for (Relation rel : relations) {
            Definition definition = rel.getDefinition();
            enc = bmgr.and(enc, definition.encodeDefinitions(activeSets.get(rel), knowledgeMap, ctx));
            if (definition instanceof Axiom) {
                // Some definitions like Rf, Co and Idd have also global constraints involved
                enc = bmgr.and(enc, ((Axiom) definition).encodeAxiom(knowledgeMap, ctx));
            }
        }

        for (Axiom ax : axioms) {
            enc = bmgr.and(enc, ax.encodeAxiom(knowledgeMap, ctx));
        }

        return enc;
    }

    // This is some temporary code to test the results of the analysis
    public static void test(VerificationTask task, Context analysisContext, SolverContext ctx) {
        NewWmm wmm = createAnarchicWmm();
        Relation rf = wmm.getRelationByNameOrTerm("rf");
        Relation co = wmm.getRelationByNameOrTerm("co");
        Relation po = wmm.newRelationWithName("po");

        Relation porf = wmm.newRelation();
        Relation poco = wmm.newRelation();
        Relation recRel = wmm.newRelation();

        wmm.addDefinition(new Union(porf, po, rf));
        wmm.addDefinition(new Intersection(poco, po, co));
        wmm.addDefinition(new Intersection(recRel, recRel, po));
        wmm.addAxiom(new Acyclic(porf));
        wmm.addAxiom(new Acyclic(poco));

        // ---- Assume po ---- (We have not definition for po yet)
        TupleSet enabled = new TupleSet();
        for (Thread t : task.getProgram().getThreads()) {
            List<Event> events = t.getCache().getEvents(FilterBasic.get(Tag.VISIBLE));
            for (int i = 1; i < events.size(); i++) {
                enabled.add(new Tuple(events.get(i-1), events.get(i)));
            }
        }
        wmm.addAxiom(new Assumption(po, new TupleSet(), enabled));


        long time = System.nanoTime();
        NewRelationAnalysis analysis = new NewRelationAnalysis(wmm);
        Map<Relation, Knowledge> know = analysis.computeKnowledge(task, analysisContext);
        Map<Relation, TupleSet> activeSet = analysis.computeActiveSets(know);
        System.out.println("Analysis time test 1 : " + (System.nanoTime() - time) / 1000000 + "ms");

        time = System.nanoTime();
        EncodingContext encCtx = new EncodingContext(ctx, analysisContext);
        BooleanFormula formula = wmm.encode(new RelationAnalysisResult(know, activeSet), encCtx);
        System.out.println("Formula construction time test 1 : " + (System.nanoTime() - time) / 1000000 + "ms");

    }

    public static void test2(VerificationTask task, Context analysisContext) {
        NewWmm wmm = createEmptyWmm();
        for (com.dat3m.dartagnan.wmm.Relation rel : task.getRelations()) {
            wmm.newRelationWithName(rel.getName());
        }

        for (com.dat3m.dartagnan.wmm.Relation rel : task.getRelations()) {
            Relation r = wmm.getRelationByNameOrTerm(rel.getName());
            if (rel.isRecursiveRelation()) {
                rel = rel.getInner();
            }
            if (rel.getDependencies().size() == 2)  {
                Relation r1 = wmm.getRelationByNameOrTerm(rel.getDependencies().get(0).getName());
                Relation r2 = wmm.getRelationByNameOrTerm(rel.getDependencies().get(1).getName());
                wmm.addDefinition(new Union(r, r1, r2));
            } else if (rel.getDependencies().size() == 1) {
                Relation r1 = wmm.getRelationByNameOrTerm(rel.getDependencies().get(0).getName());
                wmm.addDefinition(new Intersection(r, r1));
            }
        }

        for (com.dat3m.dartagnan.wmm.axiom.Axiom ax : task.getAxioms()) {
            wmm.addAxiom(new Acyclic(wmm.getRelationByNameOrTerm(ax.getRelation().getName())));
        }

        System.out.print(wmm);
        long time = System.nanoTime();
        NewRelationAnalysis analysis = new NewRelationAnalysis(wmm);
        Map<Relation, Knowledge> know = analysis.computeKnowledge(task, analysisContext);
        Map<Relation, TupleSet> activeSet = analysis.computeActiveSets(know);
        System.out.println("Analysis Time test 2 : " + (System.nanoTime() - time) / 1000);
    }

}
