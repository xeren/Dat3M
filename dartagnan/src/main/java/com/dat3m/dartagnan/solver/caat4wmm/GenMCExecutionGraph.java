package com.dat3m.dartagnan.solver.caat4wmm;

import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.CAATModel;
import com.dat3m.dartagnan.solver.caat.constraints.AcyclicityConstraint;
import com.dat3m.dartagnan.solver.caat.constraints.Constraint;
import com.dat3m.dartagnan.solver.caat.constraints.EmptinessConstraint;
import com.dat3m.dartagnan.solver.caat.constraints.IrreflexivityConstraint;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.RelationGraph;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.base.EmptyGraph;
import com.dat3m.dartagnan.solver.caat.predicates.relationGraphs.derived.*;
import com.dat3m.dartagnan.solver.caat.predicates.sets.SetPredicate;
import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.*;
import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc.*;
import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.axiom.ForceEncodeAxiom;
import com.dat3m.dartagnan.wmm.definition.*;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GenMCExecutionGraph {

    // ================== Fields =====================

    // All following variables can be considered "final".
    // Some are not declared final for purely technical reasons but all of them get
    // assigned during construction.

    private final Wmm memoryModel;
    private final BiMap<Relation, RelationGraph> relationGraphMap;
    private final BiMap<Filter, SetPredicate> filterSetMap;
    private final BiMap<Axiom, Constraint> constraintMap;

    private CAATModel caatModel;
    private GenMCEventDomain domain;

    // =================================================

    // ============= Construction & Init ===============

    public GenMCExecutionGraph(final Wmm memoryModel) {
        this.memoryModel = memoryModel;
        relationGraphMap = HashBiMap.create();
        filterSetMap = HashBiMap.create();
        constraintMap = HashBiMap.create();

        constructMappings();
    }

    public void initializeFromDomain(final GenMCEventDomain trace) {
        domain = trace;
        caatModel.initializeToDomain(trace);
    }

    // --------------------------------------------------

    private void constructMappings() {
        final Set<RelationGraph> graphs = new HashSet<>();
        final Set<Constraint> constraints = new HashSet<>();
        final DependencyGraph<Relation> dependencyGraph = DependencyGraph.from(memoryModel.getRelations());

        // Special treatment for recursive relations.
        for (final Set<DependencyGraph<Relation>.Node> component : dependencyGraph.getSCCs()) {
            if (component.size() > 1) {
                for (final DependencyGraph<Relation>.Node node : component) {
                    final Relation relation = node.getContent();
                    if (relation.isRecursive()) {
                        final RecursiveGraph graph = new RecursiveGraph();
                        graph.setName(relation.getNameOrTerm() + "_rec");
                        graphs.add(graph);
                        relationGraphMap.put(relation, graph);
                    }
                }
                for (final DependencyGraph<Relation>.Node node : component) {
                    final Relation relation = node.getContent();
                    if (relation.isRecursive()) {
                        // side effect leads to calculation of children
                        final RecursiveGraph graph = (RecursiveGraph) relationGraphMap.get(relation);
                        graph.setConcreteGraph(createGraphFromRelation(relation));
                    }
                }
            }
        }

        for (final Axiom axiom : memoryModel.getAxioms()) {
            if (axiom instanceof ForceEncodeAxiom || axiom.isFlagged()) {
                continue;
            }
            final Constraint constraint = getOrCreateConstraintFromAxiom(axiom);
            constraints.add(constraint);
        }

        caatModel = CAATModel.from(graphs, constraints);
    }

    // =================================================

    // ================ Accessors =======================

    public CAATModel getCAATModel() { return caatModel; }

    public GenMCEventDomain getDomain() { return domain; }

    public BiMap<Relation, RelationGraph> getRelationGraphMap() {
        return Maps.unmodifiableBiMap(relationGraphMap);
    }

    public BiMap<Axiom, Constraint> getAxiomConstraintMap() {
        return Maps.unmodifiableBiMap(constraintMap);
    }

    public RelationGraph getRelationGraph(final Relation rel) {
        return relationGraphMap.get(rel);
    }

    public RelationGraph getRelationGraphByName(final String name) {
        return getRelationGraph(memoryModel.getRelation(name));
    }

    public Constraint getConstraint(final Axiom axiom) {
        return constraintMap.get(axiom);
    }

    public Collection<Constraint> getConstraints() {
        return constraintMap.values();
    }

    // ====================================================

    // ==================== Mutation ======================

    public void backtrackTo(final int time) {
        caatModel.getHierarchy().backtrackTo(time);
    }

    // =======================================================

    // ==================== Analysis ======================

    public boolean checkInconsistency() {
        return caatModel.checkInconsistency();
    }

    // =======================================================

    //=================== Reading the WMM ====================

    private Constraint getOrCreateConstraintFromAxiom(final Axiom axiom) {
        if (constraintMap.containsKey(axiom)) {
            return constraintMap.get(axiom);
        }

        final Constraint constraint;
        final RelationGraph innerGraph = getOrCreateGraphFromRelation(axiom.getRelation());
        if (axiom.isAcyclicity()) {
            constraint = new AcyclicityConstraint(innerGraph);
        } else if (axiom.isEmptiness()) {
            constraint = new EmptinessConstraint(innerGraph);
        } else if (axiom.isIrreflexivity()) {
            constraint = new IrreflexivityConstraint(innerGraph);
        } else {
            throw new UnsupportedOperationException("The axiom " + axiom + " is not recognized.");
        }

        constraintMap.put(axiom, constraint);
        return constraint;
    }

    private RelationGraph getOrCreateGraphFromRelation(final Relation rel) {
        if (relationGraphMap.containsKey(rel)) {
            return relationGraphMap.get(rel);
        }
        final RelationGraph graph = createGraphFromRelation(rel);
        relationGraphMap.put(rel, graph);
        return graph;
    }

    private RelationGraph createGraphFromRelation(final Relation rel) {
        final RelationGraph graph;
        final Class<?> relClass = rel.getDefinition().getClass();
        final List<Relation> dependencies = rel.getDependencies();

        if (relClass == ReadFrom.class) {
            graph = new GenMCReadFromGraph();
        } else if (relClass == SameLocation.class) {
            graph = new GenMCLocationGraph();
        } else if (relClass == ProgramOrder.class) {
            graph = new GenMCProgramOrderGraph();
        } else if (relClass == Coherence.class) {
            graph = new GenMCCoherenceGraph();
        } else if (relClass == RangeIdentity.class || relClass == DomainIdentity.class) {
            final RelationGraph g = getOrCreateGraphFromRelation(dependencies.get(0));
            final ProjectionIdentityGraph.Dimension dim = relClass == RangeIdentity.class ?
                    ProjectionIdentityGraph.Dimension.RANGE :
                    ProjectionIdentityGraph.Dimension.DOMAIN;
            graph = new ProjectionIdentityGraph(g, dim);
        } else if (relClass == Inverse.class || relClass == TransitiveClosure.class) {
            final RelationGraph g = getOrCreateGraphFromRelation(dependencies.get(0));
            graph = relClass == Inverse.class ? new InverseGraph(g) : new TransitiveGraph(g);
        } else if (relClass == Union.class || relClass == Intersection.class) {
            final RelationGraph[] graphs = new RelationGraph[dependencies.size()];
            for (int i = 0; i < graphs.length; i++) {
                graphs[i] = getOrCreateGraphFromRelation(dependencies.get(i));
            }
            graph = relClass == Union.class ? new UnionGraph(graphs) :
                    new IntersectionGraph(graphs);
        } else if (relClass == Composition.class || relClass == Difference.class) {
            final RelationGraph g1 = getOrCreateGraphFromRelation(dependencies.get(0));
            final RelationGraph g2 = getOrCreateGraphFromRelation(dependencies.get(1));
            graph = relClass == Composition.class ? new CompositionGraph(g1, g2) :
                    new DifferenceGraph(g1, g2);
        } else if (relClass == CartesianProduct.class) {
            final CartesianProduct cartRel = (CartesianProduct)rel.getDefinition();
            final SetPredicate lhs = getOrCreateSetFromFilter(cartRel.getFirstFilter());
            final SetPredicate rhs = getOrCreateSetFromFilter(cartRel.getSecondFilter());
            graph = new CartesianGraph(lhs, rhs);
        } else if (relClass == AMOPairs.class) {
            graph = new GenMCAMOGraph();
        } else if (relClass == LXSXPairs.class) {
            graph = new GenMCLXSXGraph();
        } else if (relClass == External.class) {
            graph = new GenMCExternalGraph();
        } else if (relClass == Internal.class) {
            graph = new GenMCInternalGraph();
        } else if (relClass == SetIdentity.class) {
            final SetPredicate set = getOrCreateSetFromFilter(((SetIdentity) rel.getDefinition()).getFilter());
            graph = new SetIdentityGraph(set);
        } else if (relClass == Empty.class) {
            graph = new EmptyGraph();
        } else {
            final String error = String.format("Cannot handle relation %s with definition of type %s.",
                    rel, relClass.getSimpleName());
            throw new UnsupportedOperationException(error);
        }

        graph.setName(rel.getNameOrTerm());
        return graph;
    }

    private SetPredicate getOrCreateSetFromFilter(final Filter filter) {
        if (filterSetMap.containsKey(filter)) {
            return filterSetMap.get(filter);
        }

        final SetPredicate set = new StaticGenMCWMMSet(filter);
        set.setName(filter.toString());
        filterSetMap.put(filter, set);
        return set;

    }

    // =======================================================


}
