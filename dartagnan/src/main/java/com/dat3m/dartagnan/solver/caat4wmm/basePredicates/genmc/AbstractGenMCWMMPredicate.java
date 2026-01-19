package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.solver.caat.predicates.AbstractPredicate;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;

public abstract class AbstractGenMCWMMPredicate extends AbstractPredicate {

    protected GenMCEventDomain trace;
    private static boolean allowInit = true;

    @Override
    public GenMCEventDomain getDomain() {
        return trace;
    }

    @Override
    public void initializeToDomain(final Domain<?> domain) {
        super.initializeToDomain(domain);
        trace = (GenMCEventDomain) domain;
    }

    protected EventData getEvent(final int id) {
        return trace.getObjectById(id);
    }

    public static void allowInit() {
        allowInit = true;
    }

    public static void forbidInit() {
        allowInit = false;
    }

    protected boolean isDomainInitForbidden() {
        return !allowInit;
    }

    protected boolean isRepopulateForbidden() {
        return !allowInit;
    }

}
