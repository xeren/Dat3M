package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.NewRelationAnalysis;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example.*;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.base.Preconditions;

import java.util.*;

public class NewWmm {

    // ============================== Properties ==============================

    private final Set<AxiomaticConstraint> axiomaticConstraints = new HashSet<>();
    private final Set<Relation> relations = new HashSet<>();
    private final Map<String, Relation> name2RelMap = new HashMap<>();

    private int nextId = 0;

    public Set<Relation> getRelations() { return relations; }
    public Set<AxiomaticConstraint> getAxiomaticConstraints() { return axiomaticConstraints; }

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
        wmm.newRelationWithName("co"); // Technically not part of the anarchic semantics
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
            axiomaticConstraints.removeIf(ax -> ax.getConstrainedRelations().contains(rel));

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

        updateMetadata();

        return oldDefinition;
    }

    public boolean addAxiom(AxiomaticConstraint axiom) {
        Preconditions.checkArgument(relations.containsAll(axiom.getConstrainedRelations()),
                "The axiomatic constraint refers to relations that are not part of this Wmm");
        return axiomaticConstraints.add(axiom);
    }

    public boolean removeAxiom(AxiomaticConstraint axiom) {
        return axiomaticConstraints.remove(axiom);
    }

    // =========================================================================


    private void updateMetadata() {
        DependencyGraph<Relation> depGraph = DependencyGraph.from(relations);
        for (Set<DependencyGraph<Relation>.Node> scc : depGraph.getSCCs()) {
            boolean isRec = scc.size() > 1;
            scc.forEach(r -> r.getContent().setIsRecursive(isRec));

            if (scc.size() == 1) {
                // For strange SCCs with a single self-loop (i.e. "let rec A = A")
                Relation rel = scc.stream().findAny().get().getContent();
                rel.setIsRecursive(rel.getDependencies().contains(rel));
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        DependencyGraph<Relation> depGraph = DependencyGraph.from(relations);
        for (Relation rel : depGraph.getNodeContents()) {
            builder.append(rel).append("\n");
        }

        for (AxiomaticConstraint ax : axiomaticConstraints) {
            builder.append(ax).append("\n");
        }

        return builder.toString();
    }


    // ============================== Testing code ==============================

    // This is some temporary code to test the results of the analysis
    public static void test(VerificationTask task, Context analysisContext) {
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

        // ---- Assume po ----
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
    }

    public static void test2(VerificationTask task, Context analysisContext) {
        NewWmm wmm = createEmptyWmm();
        for (com.dat3m.dartagnan.wmm.relation.Relation rel : task.getMemoryModel().getRelations()) {
            wmm.newRelationWithName(rel.getName());
        }

        for (com.dat3m.dartagnan.wmm.relation.Relation rel : task.getMemoryModel().getRelations()) {
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

        for (Axiom ax : task.getMemoryModel().getAxioms()) {
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
