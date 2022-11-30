package com.dat3m.dartagnan.program.event.core;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.expression.BExpr;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.dat3m.dartagnan.program.event.Tag.ANY;
import static com.google.common.base.Preconditions.checkState;

/**
 * The verifier ignores executions that do not contain all instances of this class whose condition evaluates to true.
 */
public class ForceExecution extends Event {

    final BExpr condition;

    public ForceExecution(BExpr c) {
        condition = c;
        addFilters(ANY);
    }

    /**
     * Checks whether the condition of this event is a constant.
     * @return The condition is true.
     * @throws IllegalStateException The condition is variable.
     */
    public boolean isEnabled() {
        if (condition.isTrue())
            return true;
        checkState(condition.isFalse(), "Non-constant expression %s at program line %s", condition, cLine);
        return false;
    }

    @Override
    public Collection<BooleanFormula> encodeExec(EncodingContext ctx) {
        Collection<BooleanFormula> enc = super.encodeExec(ctx);
        if (!isEnabled()) {
            return enc;
        }
        List<BooleanFormula> x = new ArrayList<>(enc.size() + 1);
        x.addAll(enc);
        x.add(ctx.execution(this));
        return x;
    }
}
