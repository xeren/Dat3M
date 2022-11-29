package com.dat3m.dartagnan.program.event.core;

import com.dat3m.dartagnan.expression.BExpr;
import com.dat3m.dartagnan.expression.IExpr;

/**
 * Markers in a program that are assumed to be executed in a predefined order.
 * Embedded into a special relation `schedule` of the memory model.
 */
public final class SchedulePoint extends ForceExecution {

    private final IExpr rank;

    public SchedulePoint(BExpr c, IExpr r) {
        super(c);
        rank = r;
    }

    public int getRank() {
        return rank.reduce().getValueAsInt();
    }

    @Override
    public SchedulePoint getCopy() {
        return new SchedulePoint(condition, rank);
    }

}
