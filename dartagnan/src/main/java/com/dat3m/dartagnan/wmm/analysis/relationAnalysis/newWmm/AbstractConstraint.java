package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.google.common.collect.Iterables;

public abstract class AbstractConstraint implements Constraint {

    protected VerificationTask task;
    protected Context analysisContext;

    @Override
    public void initializeToTask(VerificationTask task, Context analysisContext) {
        this.task = task;
        this.analysisContext = analysisContext;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractConstraint that = (AbstractConstraint) o;
        return this.getConstrainedRelations().equals(that.getConstrainedRelations());
    }

    @Override
    public int hashCode() {
        return getConstrainedRelations().hashCode() + 31 * this.getClass().hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s(%s)",
                getClass().getSimpleName(),
                String.join(", ", Iterables.transform(getConstrainedRelations(), Relation::getNameOrTerm))
        );
    }
}
