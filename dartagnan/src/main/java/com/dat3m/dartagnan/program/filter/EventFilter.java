package com.dat3m.dartagnan.program.filter;

import java.util.Collection;

import com.dat3m.dartagnan.program.event.Event;

public class EventFilter extends Filter {

    private final Collection<? extends Event> events;

    EventFilter(Collection<? extends Event> events){
        this.events = events;
    }

    public Collection<? extends Event> getEvents() {
        return events;
    }

    @Override
    public boolean apply(Event event){
        return events.contains(event);
    }

    @Override
    public String toString(){
        StringBuilder builder = new StringBuilder();
        for (Event event : events) {
            builder.append(event.toString())
                    .append(", ");
        }
        builder.delete(builder.length() - 2, builder.length());
        return builder.toString();
    }

    @Override
    public int hashCode() {
        int hashCode = 0;
        for (Event event : events) {
            hashCode += event.hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EventFilter fObj = (EventFilter) obj;
        for (Event event : fObj.getEvents()) {
            if (!apply(event)) {
                return false;
            }
        }
        for (Event event : getEvents()) {
            if (!fObj.apply(event)) {
                return false;
            }
        }
        return true;
    }
}
