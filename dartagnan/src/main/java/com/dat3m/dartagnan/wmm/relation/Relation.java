package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Constraint;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.relation.binary.BinaryRelation;
import com.dat3m.dartagnan.wmm.relation.unary.UnaryRelation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;

/**
 *
 * @author Florian Furbach
 */
public abstract class Relation implements Constraint {

    protected String name;

    protected VerificationTask task;
    protected Context analysisContext;

    protected TupleSet minTupleSet = null;
    protected TupleSet maxTupleSet = null;

    public Relation() { }

    @Override
    public List<Relation> getDependencies() {
        return Collections.emptyList();
    }

    // TODO: The following two methods are provided because currently Relations are treated as three things:
    //  data objects, static analysers (relation analysis) and encoders of said data objects.
    //  Once we split these aspects, we might get rid of these methods

    // TODO: We misuse <task> as data object and analysis information object.
    // Due to partaking in relation analysis
    public void initializeRelationAnalysis(VerificationTask task, Context context) {
        this.task = task;
        this.analysisContext = context;
        this.maxTupleSet = null;
        this.minTupleSet = null;
    }

    public abstract TupleSet getMinTupleSet();

    public abstract TupleSet getMaxTupleSet();

    public TupleSet getMinTupleSetRecursive(){
        return getMinTupleSet();
    }

    public TupleSet getMaxTupleSetRecursive(){
        return getMaxTupleSet();
    }

    public String getName() {
        return name != null ? name : getTerm();
    }

    public Relation setName(String name){
        this.name = name;
        return this;
    }

    public abstract String getTerm();

    public boolean getIsNamed(){
        return name != null;
    }

    @Override
    public String toString(){
        if(name != null){
            return name + " := " + getTerm();
        }
        return getTerm();
    }

    @Override
    public int hashCode(){
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        return getName().equals(((Relation)obj).getName());
    }

    public BooleanFormula getSMTVar(Tuple edge, SolverContext ctx) {
        return !getMaxTupleSet().contains(edge) ?
        		ctx.getFormulaManager().getBooleanFormulaManager().makeFalse() :
                edge(getName(), edge.getFirst(), edge.getSecond(), ctx);
    }

    public final BooleanFormula getSMTVar(Event e1, Event e2, SolverContext ctx) {
        return getSMTVar(new Tuple(e1, e2), ctx);
    }

    protected void removeMutuallyExclusiveTuples(Set<Tuple> tupleSet) {
        ExecutionAnalysis exec = analysisContext.requires(ExecutionAnalysis.class);
        tupleSet.removeIf(t -> exec.areMutuallyExclusive(t.getFirst(), t.getSecond()));
    }

    // ========================== Utility methods =========================
    
    public boolean isStaticRelation() {
    	return this instanceof StaticRelation;
    }
    
    public boolean isUnaryRelation() {
    	return this instanceof UnaryRelation;
    }
    
    public boolean isBinaryRelation() {
    	return this instanceof BinaryRelation;
    }
    
    public boolean isRecursiveRelation() {
    	return this instanceof RecursiveRelation;
    }

    public Relation getInner() {
        return (isUnaryRelation() || isRecursiveRelation()) ? getDependencies().get(0) : null;
    }
    
    public Relation getFirst() {
    	return isBinaryRelation() ? getDependencies().get(0) : null;
    }
    
    public Relation getSecond() {
    	return isBinaryRelation() ? getDependencies().get(1) : null;
    }
}