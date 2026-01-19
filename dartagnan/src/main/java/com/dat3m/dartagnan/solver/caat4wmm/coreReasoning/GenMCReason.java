package com.dat3m.dartagnan.solver.caat4wmm.coreReasoning;

import com.dat3m.dartagnan.utils.logic.Conjunction;

import java.util.List;

public class GenMCReason extends Conjunction<CoreLiteral> {

    private final CoreLiteral[] literals;

    GenMCReason(final List<CoreLiteral> literals) {
        super(literals);
        this.literals = getLiterals().toArray(CoreLiteral[]::new);
    }

    public CoreLiteral[] getLiteralsToIterateOver() {
        return literals;
    }
}
