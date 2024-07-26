package com.dat3m.dartagnan.verification.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    public Filter eventFilter() {
        return Filter.byEvents(getElements()
                .stream()
                .map(EventData::getEvent)
                .toList());
    }
}
