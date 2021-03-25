package com.dat3m.dartagnan.wmm;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.relation.base.stat.RelCartesian;
import com.dat3m.dartagnan.wmm.relation.base.stat.RelSetIdentity;
import com.dat3m.dartagnan.wmm.axiom.Acyclic;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.utils.*;
import com.dat3m.dartagnan.wmm.utils.alias.AliasAnalysis;
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

    public Acyclic getHappensBefore() {
        return axioms.stream().filter(Acyclic.class::isInstance).map(Acyclic.class::cast)
            .filter(a->a.getRel().getName().equals("hb")).findAny().orElse(null);
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

	private boolean[][] compute(HashMap<Relation,boolean[][]> binding, int size, Class<?extends Relation> cls, Object... arg){
		return binding.computeIfAbsent(relationRepository.getRelation(cls,arg),k->new boolean[size][size]);
	}

	private boolean[][] compute(HashMap<Relation,boolean[][]> binding, int size, String name){
		return binding.computeIfAbsent(relationRepository.getRelation(name),k->new boolean[size][size]);
	}

	private HashMap<Relation,boolean[][]> init(FilterBasic[]... type){
		HashMap<Relation,boolean[][]> binding = new HashMap<>();
		int size = type.length;
		for(int i = 0; i < size; i++){
			for(FilterBasic ti : type[i]){
				for(int j = 0; j < size; j++)
					for(FilterBasic tj : type[j])
						compute(binding,size,RelCartesian.class,ti,tj)[i][j] = true;
				compute(binding,size,RelSetIdentity.class,ti)[i][i] = true;
			}
		}
		return binding;
	}

	public boolean isLocalConsistentRf(){
		HashMap<Relation,boolean[][]> binding = init(
			new FilterBasic[]{FilterBasic.get(EType.READ),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)},
			new FilterBasic[]{FilterBasic.get(EType.WRITE),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)});
		compute(binding,2,"po")[0][1] = true;
		compute(binding,2,"rf")[1][0] = true;
		boolean[][] loc = compute(binding,2,"loc");
		loc[0][0] = loc[0][1] = loc[1][0] = loc[1][1] = true;
		boolean[][] int_ = compute(binding,2,"int");
		int_[0][1] = int_[1][0] = true;
		for(Axiom a: axioms)
			if(!a.test(a.getRel().test(binding,2)))
				return true;
		return false;
	}

	public boolean isLocalConsistentCo(){
		FilterBasic[] filter = new FilterBasic[]{FilterBasic.get(EType.WRITE),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)};
		HashMap<Relation,boolean[][]> binding = init(filter,filter);
		compute(binding,2,"po")[0][1] = true;
		compute(binding,2,"co")[1][0] = true;
		boolean[][] loc = compute(binding,2,"loc");
		loc[0][0] = loc[0][1] = loc[1][0] = loc[1][1] = true;
		boolean[][] int_ = compute(binding,2,"int");
		int_[0][1] = int_[1][0] = true;
		for(Axiom a: axioms)
			if(!a.test(a.getRel().test(binding,2)))
				return true;
		return false;
	}

	public boolean isLocalConsistentFre(){
		HashMap<Relation,boolean[][]> binding = init(
			new FilterBasic[]{FilterBasic.get(EType.WRITE),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)},
			new FilterBasic[]{FilterBasic.get(EType.WRITE),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)},
			new FilterBasic[]{FilterBasic.get(EType.READ),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)});
		compute(binding,3,"co")[0][1] = true;
		compute(binding,3,"po")[1][2] = true;
		compute(binding,3,"rf")[0][2] = true;
		boolean[][] loc = compute(binding,3,"loc");
		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 3; j++)
				loc[i][j] = true;
		boolean[][] int_ = compute(binding,3,"int");
		int_[1][2] = int_[2][1] = true;
		boolean[][] ext = compute(binding,3,"ext");
		ext[0][1] = ext[0][2] = ext[1][0] = ext[2][0] = true;
		for(Axiom a: axioms)
			if(!a.test(a.getRel().test(binding,3)))
				return true;
		return false;
	}

	public boolean isLocalConsistentFri(){
		HashMap<Relation,boolean[][]> binding = init(
			new FilterBasic[]{FilterBasic.get(EType.WRITE),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)},
			new FilterBasic[]{FilterBasic.get(EType.WRITE),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)},
			new FilterBasic[]{FilterBasic.get(EType.READ),FilterBasic.get(EType.MEMORY),FilterBasic.get(EType.ANY)});
		compute(binding,3,"co")[0][1] = true;
		compute(binding,3,"po")[1][2] = true;
		compute(binding,3,"rf")[0][2] = true;
		boolean[][] loc = compute(binding,3,"loc");
		boolean[][] int_ = compute(binding,3,"int");
		for(int i = 0; i < 3; i++)
			for(int j = 0; j < 3; j++)
				loc[i][j] = int_[i][j] = true;
		for(Axiom a: axioms)
			if(!a.test(a.getRel().test(binding,3)))
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

        for(FilterAbstract filter : filters.values()){
            filter.initialise();
        }

		settings.setFlag(Settings.FLAG_CURRENT_MODEL_LOCAL_CO,isLocalConsistentCo());
		settings.setFlag(Settings.FLAG_CURRENT_MODEL_LOCAL_RF,isLocalConsistentCo());

        for(Relation relation : relationRepository.getRelations()){
            relation.initialise(program, ctx, settings);
        }

        for(RecursiveGroup recursiveGroup : recursiveGroups){
            recursiveGroup.initMaxTupleSets();
        }

        for (Axiom ax : axioms) {
            ax.getRel().initMaxTupleSet();
        }

        for(String relName : baseRelations){
            relationRepository.getRelation(relName).initMaxTupleSet();
        }

        if(settings.getDrawGraph()){
            for(String relName : settings.getGraphRelations()){
                Relation relation = relationRepository.getRelation(relName);
				if(null==relation)
					continue;
				ArrayList<Tuple> set = new ArrayList<>(relation.size());
				for(Tuple t : relation.getMaxTupleSet())
					set.add(t);
				relation.addEncodeTupleSet(set);
            }
        }

        for (Axiom ax : axioms) {
			ax.getEncodeTupleSet();
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
