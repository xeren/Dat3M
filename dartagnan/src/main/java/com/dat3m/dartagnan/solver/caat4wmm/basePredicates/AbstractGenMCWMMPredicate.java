package com.dat3m.dartagnan.solver.caat4wmm.basePredicates;

import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.solver.caat.predicates.AbstractPredicate;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.verification.model.GenMCExecutionModel;
import com.google.common.base.Preconditions;

public abstract class AbstractGenMCWMMPredicate extends AbstractPredicate {
    protected GenMCExecutionModel model;

    @Override
    public GenMCEventDomain getDomain() {
        return (GenMCEventDomain)domain;
    }

    @Override
    public void initializeToDomain(Domain<?> domain) {
        Preconditions.checkArgument(domain instanceof GenMCEventDomain,
                "Incompatible domain. Expected " + GenMCEventDomain.class.getSimpleName());
        super.initializeToDomain(domain);
        model = ((GenMCEventDomain)domain).getExecution();
    }

    protected EventData getEvent(int id) {
        return getDomain().getObjectById(id);
    }
}
