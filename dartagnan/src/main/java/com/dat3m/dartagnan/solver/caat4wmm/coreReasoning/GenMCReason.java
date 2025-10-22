package com.dat3m.dartagnan.solver.caat4wmm.coreReasoning;

import com.dat3m.dartagnan.utils.logic.Conjunction;

import java.util.List;
import java.util.function.Predicate;

public class GenMCReason extends Conjunction<CoreLiteral> {

    private final CoreLiteral[] literals;

    GenMCReason(final List<CoreLiteral> literals) {
        super(literals);
        this.literals = getLiterals().toArray(CoreLiteral[]::new);
    }

    public CoreLiteral[] getLiteralsToIterateOver() {
        return literals;
    }

    @Override
    public boolean evaluate(final Predicate<CoreLiteral> literalEvaluator) {
        for (final CoreLiteral literal : literals) {
            if (!literalEvaluator.test(literal)) {
                return false;
            }
        }
        return true;
    }
}
