package com.dat3m.dartagnan.program.event.core;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.event.visitors.EventVisitor;

import static com.dat3m.dartagnan.program.event.Tag.VISIBLE;

/**
 * Markers in a program that are assumed to be executed in a predefined order.
 * Embedded into a special relation `schedule` of the memory model.
 */
public final class SchedulePoint extends ForceExecution {

    private final IExpr rank;

    public SchedulePoint(ExprInterface c, IExpr r) {
        super(c);
        rank = r;
        addFilters(VISIBLE);
    }

    protected SchedulePoint(SchedulePoint other) {
		super(other);
		this.rank = other.rank;
    }

    public int getRank() {
        return rank.reduce().getValueAsInt();
    }

    @Override
    public String toString() {
		return String.format("#__VERIFIER_schedule(%s, %s)", condition, rank);
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public SchedulePoint getCopy() {
        return new SchedulePoint(this);
    }

    	// Visitor
	// -----------------------------------------------------------------------------------------------------------------

	// @Override
	// public <T> T accept(EventVisitor<T> visitor) {
	// 	return visitor.visitSchedulePoint(this);
	// }

}
