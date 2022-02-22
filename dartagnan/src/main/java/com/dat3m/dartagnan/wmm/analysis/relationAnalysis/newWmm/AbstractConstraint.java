package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;

public abstract class AbstractConstraint implements Constraint {

    protected VerificationTask task;
    protected Context analysisContext;

    @Override
    public void initializeToTask(VerificationTask task, Context analysisContext) {
        this.task = task;
        this.analysisContext = analysisContext;
    }
}
