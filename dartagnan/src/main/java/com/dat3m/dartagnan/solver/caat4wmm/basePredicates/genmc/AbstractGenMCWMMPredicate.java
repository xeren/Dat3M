package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.solver.caat.predicates.AbstractPredicate;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;
import com.google.common.base.Preconditions;


public abstract class AbstractGenMCWMMPredicate extends AbstractPredicate {

    protected GenMCEventDomain trace;

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
    }

    protected EventData getEvent(int id) {
        return getDomain().getElements().stream()
                .filter(e -> e.getId() == id)
                .findAny()
                .orElse(null);
    }

}
