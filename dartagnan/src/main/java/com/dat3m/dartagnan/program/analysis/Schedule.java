package com.dat3m.dartagnan.program.analysis;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.SchedulePoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.dat3m.dartagnan.program.event.Tag.VISIBLE;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Scheduling fixes a certain order of events in an acyclic program in relation to the memory model.
 * <p>
 * Example: {@code acyclic po-loc | com | schedule}
 * where {@code schedule} is a customized relation
 * with the externalization {@code [E131->E44, E44->E89, E89->E132]}
 */
public final class Schedule {

    private final List<Event> events = new ArrayList<>();

    private Schedule() {}

    public static Schedule fromConfig(Program p) {
        Schedule s = new Schedule();
        s.run(p);
        return s;
    }

    public List<Event> getSchedule() {
        return List.copyOf(events);//TODO view
    }

    private void run(Program p) {
        p.getEvents().stream()
                .filter(SchedulePoint.class::isInstance).map(SchedulePoint.class::cast)
                .filter(SchedulePoint::isEnabled)
                .sorted(Comparator.comparingInt(SchedulePoint::getRank))
                .forEach(events::add);
        //TODO checkArgument(annotations unique by rank);
        checkArgument(events.stream().noneMatch(Objects::isNull), "missing scheduled event");
    }
}
