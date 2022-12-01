package com.dat3m.dartagnan.program.event.core;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.visitors.EventVisitor;
import com.google.common.collect.ImmutableSet;

import static com.dat3m.dartagnan.program.event.Tag.VISIBLE;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Markers in a program that are assumed to be executed in a predefined order.
 * Embedded into a special relation `schedule` of the memory model.
 */
public final class SchedulePoint extends ForceExecution {

    private IExpr rank;

    public SchedulePoint(ExprInterface c, IExpr r) {
        super(c);
        rank = checkNotNull(r);
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

    @Override
    public ImmutableSet<Register> getDataRegs() {
        ImmutableSet.Builder<Register> builder = ImmutableSet.builder();
        builder.addAll(super.getDataRegs());
        builder.addAll(rank.getRegs());
        return builder.build();
    }

    // Preprocessing
    // -----------------------------------------------------------------------------------------------------------------

    public IExpr getRankExpression() {
        return rank;
    }

    public void setRankExpression(IExpr r) {
        rank = checkNotNull(r);
    }

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public SchedulePoint getCopy() {
        return new SchedulePoint(this);
    }

    // Visitor
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public <T> T accept(EventVisitor<T> visitor) {
        return visitor.visitSchedulePoint(this);
    }

}
