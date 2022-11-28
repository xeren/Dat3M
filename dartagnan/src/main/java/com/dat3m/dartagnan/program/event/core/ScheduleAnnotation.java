package com.dat3m.dartagnan.program.event.core;

import com.dat3m.dartagnan.program.event.Tag;

public final class ScheduleAnnotation extends Skip {

    private final int rank;

    public ScheduleAnnotation(int r) {
        rank = r;
        addFilters(Tag.ASSUME_EXECUTED);
    }

    public int getRank() {
        return rank;
    }

    @Override
    public Skip getCopy() {
        return new ScheduleAnnotation(rank);//FIXME define behavior
    }

}
