package com.dat3m.dartagnan.verification.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Init;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.core.threading.ThreadCreate;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.domain.Domain;

public class SimpleEventDomain implements Domain<EventData> {

    private List<EventData> events;

    protected SimpleEventDomain() {
        this.events = new ArrayList<>();
    }

    protected SimpleEventDomain(Collection<EventData> events) {
        this.events = new ArrayList<>(events);
    }

    protected void addEvent(EventData event) {
        events.add(event);
    }

    @Override
    public int size() {
        return events.size();
    }

    @Override
    public List<EventData> getElements() {
        return events;
    }

    @Override
    public int getId(Object obj) {
        return events.indexOf(obj);
    }

    @Override
    public EventData getObjectById(int id) {
        try {
            return events.get(id);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public EventData getLast() {
        return events.get(events.size() - 1);
    }

    public EventData removeLast() {
        EventData last = getLast();
        events.remove(last);
        return last;
    }

    public Filter eventFilter() {
        return Filter.byEvents(getElements()
                .stream()
                .map(EventData::getEvent)
                .toList());
    }

    @Override
    public String toString() {
        return toString(getElements());
    }

    public String toString(Filter eventFilter) {
        return toString(getElements(), eventFilter);
    }

    public static String toString(Collection<EventData> events, Filter eventFilter) {
        return toString(events.stream()
                .filter(event -> eventFilter.apply(event.getEvent()))
                .toList());
    }

    public static String toString(Collection<EventData> events) {
        StringBuilder builder = new StringBuilder();
        Event pred = null;
        for (EventData eventData : events) {
            builder.append('\n');
            Event event = eventData.getEvent();
            if (pred != null && pred.getThread() != event.getThread() && !(event instanceof Init)) {
                builder.append("\n");
            }
            builder.append(event.getThread().getId())
                    .append(": ");
            builder.append(event.getGlobalId())
                    .append(": ");
            builder.append(event.getClass().getSimpleName())
                    .append(": ");
            builder.append(event);
            if (eventData.getReadFrom() != null) {
                Event readFrom = eventData.getReadFrom().getEvent();
                builder.append(" reads from ")
                        .append(readFrom.getGlobalId())
                        .append(": ")
                        .append(readFrom);
            }
            pred = event;
        }
        return builder.toString();
    }

    public String toRelevantEventsString() {
        return toRelevantEventsString(getElements());
    }

    public static String toRelevantEventsString(Collection<EventData> events) {
        List<EventData> filteredEvents = new ArrayList<>();
        for (EventData eventData : events) {
            Event event = eventData.getEvent();
            if ("pthread_join".equals(event.getFunction().getName())) {
                continue;
            }
            if (event.getPredecessor() instanceof Init) {
                continue;
            }
            if (event instanceof Init) {
                continue;
            }
            if (event.getSuccessor() instanceof Init) {
                continue;
            }
            if (event instanceof RegWriter writer && isSynthetic(writer.getResultRegister().getName())) {
                continue;
            }
            if (event.getPredecessor() instanceof ThreadCreate) {
                continue;
            }
            if (isThreadEndStore(eventData)) {
                continue;
            }
            filteredEvents.add(eventData);
        }
        return toString(filteredEvents, Filter.byTag(Tag.VISIBLE));
    }

    private static boolean isSynthetic(String name) {
        return name.startsWith("__");
    }

    static boolean isThreadEndStore(EventData store) {
        return store.getEvent().getSuccessor() instanceof Label label && label.getThread().getEntry().isSpawned() && label.getName().startsWith("END_OF_T");
    }
}
