package com.dat3m.dartagnan.verification.model;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

import static com.dat3m.dartagnan.program.event.Tag.*;


//EventData represents all data associated with an event in a concrete model.
public class EventData implements Comparable<EventData> {
    private final Event event;
    private EventData readFrom;
    private int id = -1;
    private int localId = -1;
    private Object value;
    private Object accessedAddress;
    private int coIndex = Integer.MIN_VALUE;
    private EventData coPredecessor;
    private EventData coSuccessor;
    private boolean wasExecuted;
    private Boolean isNonDet = null;

    public EventData(Event e) {
        this.event = e;
    }

    public Event getEvent() {
    	return event;
    }
    public Thread getThread() {
        return event.getThread();
    }

    public int getId() {
    	return id;
    }
    public void setId(int newId) {
    	id = newId;
    }

    public int getLocalId() {
    	return localId;
    }
    public void setLocalId(int newId) {
    	localId = newId;
    }

    public Object getAccessedAddress() {
    	return accessedAddress;
    }
    public void setAccessedAddress(Object address) {
        accessedAddress = address;
    }

    public Object getValue() {
    	return value;
    }
    void setValue(Object val) {
    	value = val;
    }

    public EventData getReadFrom() {
    	return readFrom;
    }
    public void setReadFrom(EventData store) {
        if (store != null) {
            Preconditions.checkArgument(store.isWrite());
            isNonDet = store.isNonDet;
        }
    	readFrom = store;
    }

    public Boolean isNonDet() {
    	return isNonDet;
    }
    public void setNonDet(Boolean isNonDet) {
        this.isNonDet = isNonDet;
    }
    public void setNonDet(boolean isNonDet) {
        this.isNonDet = isNonDet;
    }

    public boolean wasExecuted() {
    	return wasExecuted;
    }
    public void setWasExecuted(boolean flag) {
    	wasExecuted = flag;
    }

    public int getCoherenceIndex() { return coIndex; }
    void setCoherenceIndex(int index) { coIndex = index; }
    public EventData getCoPredecessor() { return coPredecessor; }
    public void setCoPredecessor(EventData predecessor) { coPredecessor = predecessor; }
    public EventData getCoSuccessor() { return coSuccessor; }
    public void setCoSuccessor(EventData successor) { coSuccessor = successor; }

    public List<EventData> getCoPredecessors() {
        List<EventData> events = new ArrayList<>();
        EventData cur = this;
        while (cur != null) {
            events.add(cur);
            cur = cur.getCoPredecessor();
        }
        return events;
    }

    public List<EventData> getCoSuccessors() {
        List<EventData> events = new ArrayList<>();
        EventData cur = this;
        while (cur != null) {
            events.add(cur);
            cur = cur.getCoSuccessor();
        }
        return events;
    }

    public boolean isMemoryEvent() { return event.hasTag(MEMORY); }
    public boolean isInit() {
        return event.hasTag(INIT);
    }
    public boolean isWrite() { return event.hasTag(WRITE); }
    public boolean isRead() { return event.hasTag(READ); }
    public boolean isFence() { return event.hasTag(FENCE); }
    public boolean isJump() {
        return event instanceof CondJump;
    }
    public boolean isExclusive() {
    	return event.hasTag(EXCL);
    }
    public boolean isRMW() {
    	return event.hasTag(RMW);
    }

    public boolean hasTag(String tag) {
    	return event.hasTag(tag);
    }

    @Override
    public String toString() {
        return String.format("T%d:%d", event.getThread().getId(), localId);
    }

    @Override
    public int compareTo(EventData o) {
        return this.getId() - o.getId();
    }
}
