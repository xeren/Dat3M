package com.dat3m.dartagnan.wmm;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.relation.base.stat.RelCartesian;
import com.dat3m.dartagnan.wmm.relation.base.stat.RelSetIdentity;
import com.dat3m.dartagnan.wmm.utils.*;
import com.dat3m.dartagnan.wmm.utils.alias.AliasAnalysis;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.RecursiveRelation;
import com.dat3m.dartagnan.wmm.relation.Relation;

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

    private Program program;

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

	public boolean isLocalConsistent(){
		HashMap<Relation,boolean[][]> binding = new HashMap<>();
		boolean[][] co = binding.computeIfAbsent(relationRepository.getRelation("co"),k->new boolean[2][2]);
		boolean[][] loc = binding.computeIfAbsent(relationRepository.getRelation("loc"),k->new boolean[2][2]);
		boolean[][] po = binding.computeIfAbsent(relationRepository.getRelation("po"),k->new boolean[2][2]);
		boolean[][] int_ = binding.computeIfAbsent(relationRepository.getRelation("int"),k->new boolean[2][2]);
		po[0][1] = int_[0][1] = int_[1][0] = true;
		co[1][0] = loc[0][0] = loc[0][1] = loc[1][0] = loc[1][1] = true;
		FilterBasic[] filter = new FilterBasic[]{FilterBasic.get(EType.WRITE),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)};
		for(FilterBasic a : filter){
			for(FilterBasic b : filter){
				boolean[][] r = binding.computeIfAbsent(relationRepository.getRelation(RelCartesian.class,a,b),k->new boolean[2][2]);
				r[0][0] = r[0][1] = r[1][0] = r[1][1] = true;
			}
			boolean[][] r = binding.computeIfAbsent(relationRepository.getRelation(RelSetIdentity.class,a),k->new boolean[2][2]);
			r[0][0] = r[1][1] = true;
		}
		for(Axiom a: axioms)
			if(!a.test(a.getRel().test(binding,2)))
				return true;
		return false;
	}

    public BoolExpr encode(Program program, Context ctx, Settings settings) {
        this.program = program;
        new AliasAnalysis().calculateLocationSets(this.program, settings.getAlias());

        for(String relName : baseRelations){
            relationRepository.getRelation(relName);
        }

        for (Axiom ax : axioms) {
            ax.getRel().updateRecursiveGroupId(ax.getRel().getRecursiveGroupId());
        }

        for(RecursiveGroup recursiveGroup : recursiveGroups){
            recursiveGroup.setDoRecurse();
        }

        for(FilterAbstract filter : filters.values()){
            filter.initialise();
        }

        for(Relation relation : relationRepository.getRelations()){
            relation.initialise(program, ctx, settings);
        }

        for(RecursiveGroup recursiveGroup : recursiveGroups){
            recursiveGroup.initMaxTupleSets();
        }

        for (Axiom ax : axioms) {
            ax.getRel().getMaxTupleSet();
        }

        for(String relName : baseRelations){
            relationRepository.getRelation(relName).getMaxTupleSet();
        }

        if(settings.getDrawGraph()){
            for(String relName : settings.getGraphRelations()){
                Relation relation = relationRepository.getRelation(relName);
                if(relation != null){
                    relation.addEncodeTupleSet(relation.getMaxTupleSet());
                }
            }
        }

        for (Axiom ax : axioms) {
            ax.getRel().addEncodeTupleSet(ax.getEncodeTupleSet());
        }

        Collections.reverse(recursiveGroups);
        for(RecursiveGroup recursiveGroup : recursiveGroups){
            recursiveGroup.updateEncodeTupleSets();
        }

        BoolExpr enc = ctx.mkTrue();
        for(String relName : baseRelations){
            enc = ctx.mkAnd(enc, relationRepository.getRelation(relName).encode());
        }

        if(settings.getMode() == Mode.KLEENE){
            for(RecursiveGroup group : recursiveGroups){
                enc = ctx.mkAnd(enc, group.encode(ctx));
            }
        }
        
        for (Axiom ax : axioms) {
            enc = ctx.mkAnd(enc, ax.getRel().encode());
        }
        
        return enc;
    }

    public BoolExpr consistent(Program program, Context ctx) {
        if(this.program != program){
            throw new RuntimeException("Wmm relations must be encoded before consistency predicate");
        }
        BoolExpr expr = ctx.mkTrue();
        for (Axiom ax : axioms) {
            expr = ctx.mkAnd(expr, ax.consistent(ctx));
        }
        return expr;
    }

    public BoolExpr inconsistent(Program program, Context ctx) {
        if(this.program != program){
            throw new RuntimeException("Wmm relations must be encoded before inconsistency predicate");
        }
        BoolExpr expr = ctx.mkFalse();
        for (Axiom ax : axioms) {
            expr = ctx.mkOr(expr, ax.inconsistent(ctx));
        }
        return expr;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (Axiom axiom : axioms) {
            sb.append(axiom).append("\n");
        }

        for (Relation relation : relationRepository.getRelations()) {
            if(relation.getIsNamed()){
                sb.append(relation).append("\n");
            }
        }

        for (Map.Entry<String, FilterAbstract> filter : filters.entrySet()){
            sb.append(filter.getValue()).append("\n");
        }

        return sb.toString();
    }
}
