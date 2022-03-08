package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.google.common.base.Preconditions;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDefinition extends AbstractConstraint implements Definition {

    protected final Relation definedRelation;
    protected final List<Relation> constrainedRelations;
    protected final List<Relation> dependencies;

    public AbstractDefinition(Relation definedRel, List<Relation> dependencies) {
        Preconditions.checkNotNull(dependencies);
        definedRelation = Preconditions.checkNotNull(definedRel);
        constrainedRelations = new ArrayList<>(dependencies);
        constrainedRelations.add(definedRel);
        this.dependencies = constrainedRelations.subList(0, dependencies.size());
    }

    @Override public Relation getDefinedRelation() { return definedRelation; }
    @Override public List<Relation> getConstrainedRelations() { return constrainedRelations; }
    @Override public List<Relation> getDependencies() { return dependencies; }

    @Override
    public BooleanFormula getSMTVar(Tuple t, Knowledge kRel, EncodingContext ctx) {
        BooleanFormulaManager bmgr = ctx.getBmgr();
        if (kRel.isFalse(t)) {
            return bmgr.makeFalse();
        } else if (kRel.isTrue(t)) {
            return ctx.execPair(t);
        } else {
            return ctx.edgeVar(getDefinedRelation().getNameOrTerm(), t);
        }
    }

}
