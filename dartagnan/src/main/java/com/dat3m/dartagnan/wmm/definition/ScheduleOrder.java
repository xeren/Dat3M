package com.dat3m.dartagnan.wmm.definition;

import com.dat3m.dartagnan.wmm.Definition;
import com.dat3m.dartagnan.wmm.Relation;

public class ScheduleOrder extends Definition {

    public ScheduleOrder(Relation r0) {
        super(r0, "schedule");
    }

    @Override
    public <T> T accept(com.dat3m.dartagnan.wmm.Definition.Visitor<? extends T> visitor) {
        return visitor.visitSchedule(definedRelation);
    }
}
