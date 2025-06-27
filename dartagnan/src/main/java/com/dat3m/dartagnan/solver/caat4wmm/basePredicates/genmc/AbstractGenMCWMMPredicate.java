package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.solver.caat.predicates.AbstractPredicate;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;
import com.google.common.base.Preconditions;


public abstract class AbstractGenMCWMMPredicate extends AbstractPredicate {

    protected GenMCEventDomain trace;
    private boolean allowRepopulate;

    @Override
    public GenMCEventDomain getDomain() {
        return trace;
    }

    @Override
    public void initializeToDomain(Domain<?> domain) {
        Preconditions.checkArgument(domain instanceof GenMCEventDomain,
                "Incompatible domain. Expected " + GenMCEventDomain.class.getSimpleName());
        super.initializeToDomain(domain);
        trace = (GenMCEventDomain) domain;
        allowRepopulate = !trace.usesPatterns();
    }

    protected EventData getEvent(int id) {
        return trace.getObjectById(id);
    }

    public void allowRepopulate() {
        allowRepopulate = true;
    }

    public void forbidRepopulate() {
        allowRepopulate = false;
    }

    protected boolean isRepopulateAllowed() {
        return allowRepopulate;
    }

}
