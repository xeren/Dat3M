package com.dat3m.dartagnan.wmm;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.*;
import com.dat3m.dartagnan.wmm.utils.alias.AliasAnalysis;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.RecursiveRelation;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import java.util.*;

/**
 *
 * @author Florian Furbach
 */
public class Wmm {

    private final static ImmutableSet<String> baseRelations = ImmutableSet.of("co", "rf", "idd", "addrDirect");

    private List<Axiom> axioms = new ArrayList<>();
    private Map<String, FilterAbstract> filters = new HashMap<>();
    private RelationRepository relationRepository;
    private List<RecursiveGroup> recursiveGroups = new ArrayList<>();

    public Wmm() {
        relationRepository = new RelationRepository();
    }

    public void addAxiom(Axiom ax) {
        axioms.add(ax);
    }

    public List<Axiom> getAxioms() {
        return axioms;
    }

    public void addFilter(FilterAbstract filter) {
        filters.put(filter.getName(), filter);
    }

    public FilterAbstract getFilter(String name){
        FilterAbstract filter = filters.get(name);
        if(filter == null){
            filter = FilterBasic.get(name);
        }
        return filter;
    }

    public RelationRepository getRelationRepository(){
        return relationRepository;
    }

    public void addRecursiveGroup(Set<RecursiveRelation> recursiveGroup){
        int id = 1 << recursiveGroups.size();
        if(id < 0){
            throw new RuntimeException("Exceeded maximum number of recursive relations");
        }
        recursiveGroups.add(new RecursiveGroup(id, recursiveGroup));
    }

    public void encodeBase(EncodeContext context) {
        new AliasAnalysis().calculateLocationSets(context.program, context.settings.getAlias());

        for(String relName : baseRelations){
            relationRepository.getRelation(relName);
        }

        for(Axiom ax : axioms) {
            ax.getRel().updateRecursiveGroupId(ax.getRel().getRecursiveGroupId());
        }

        for(FilterAbstract filter : filters.values()){
            filter.initialise();
        }

        for(Relation relation : relationRepository.getRelations()){
            relation.initialise();
        }

        for(RecursiveGroup recursiveGroup : recursiveGroups){
            recursiveGroup.initMaxTupleSets(context);
        }

        for(Axiom ax : axioms) {
            ax.getRel().getMaxTupleSet(context);
        }

        for(String relName : baseRelations){
            relationRepository.getRelation(relName).getMaxTupleSet(context);
        }

        if(context.settings.getDrawGraph()){
            for(String relName : context.settings.getGraphRelations()){
                Relation relation = relationRepository.getRelation(relName);
                if(relation != null){
                    relation.addEncodeTupleSet(context, relation.getMaxTupleSet(context));
                }
            }
        }

        for(Axiom ax : axioms) {
            ax.getRel().addEncodeTupleSet(context, ax.getEncodeTupleSet(context));
        }

        Collections.reverse(recursiveGroups);
        for(RecursiveGroup recursiveGroup : recursiveGroups){
            recursiveGroup.updateEncodeTupleSets(context);
        }

        for(String relName : baseRelations){
            relationRepository.getRelation(relName).encode(context);
        }

        if(context.settings.getMode() == Mode.KLEENE){
            for(RecursiveGroup group : recursiveGroups){
                group.encode(context);
            }
        }
    }

    public void encode(EncodeContext context) {
        encodeBase(context);
        for(Axiom ax : axioms)
            ax.getRel().encode(context);
    }

    public BoolExpr consistent(Context ctx) {
        BoolExpr expr = ctx.mkTrue();
        for(Axiom ax : axioms) {
            expr = ctx.mkAnd(expr, ax.consistent(ctx));
        }
        return expr;
    }

    public BoolExpr inconsistent(Context ctx) {
        BoolExpr expr = ctx.mkFalse();
        for(Axiom ax : axioms) {
            expr = ctx.mkOr(expr, ax.inconsistent(ctx));
        }
        return expr;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        for(Axiom axiom : axioms) {
            sb.append(axiom).append("\n");
        }

        for(Relation relation : relationRepository.getRelations()) {
            if(relation.getIsNamed()){
                sb.append(relation).append("\n");
            }
        }

        for(Map.Entry<String, FilterAbstract> filter : filters.entrySet()){
            sb.append(filter.getValue()).append("\n");
        }

        return sb.toString();
    }
}
