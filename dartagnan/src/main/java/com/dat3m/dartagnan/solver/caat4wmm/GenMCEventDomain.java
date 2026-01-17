package com.dat3m.dartagnan.solver.caat4wmm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Init;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.core.RMWStoreExclusive;
import com.dat3m.dartagnan.program.event.core.Store;
import com.dat3m.dartagnan.program.event.core.threading.ThreadCreate;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.verification.model.EventData;

import static com.dat3m.dartagnan.utils.Utils.getOptimalMapCapacity;

public final class GenMCEventDomain implements Domain<EventData> {

    private static int optimalThreadMapCapacity;

    private final List<EventData> events;
    private final Map<Event, EventData> eventMap;
    private final Map<Thread, List<EventData>> threadEventsMap;
    private final Map<BigInteger, EventData> addressInits;
    private final Map<EventData, EventData> readWriteMap;
    private final Map<EventData, List<EventData>> writeReadsMap;
    private final Map<EventData, EventData> lxsxPairs;
    private final Map<Register, EventData> lastRegWriters;

    public GenMCEventDomain(int threadCount) {
        setThreadCount(threadCount);
        this.events = new ArrayList<>();
        this.eventMap = new HashMap<>();
        this.threadEventsMap = new HashMap<>(optimalThreadMapCapacity);
        this.addressInits = new HashMap<>();
        this.readWriteMap = new HashMap<>();
        this.writeReadsMap = new HashMap<>();
        this.lxsxPairs = new HashMap<>();
        this.lastRegWriters = new HashMap<>();
    }

    public GenMCEventDomain(final List<EventData> oldEvents, final List<EventData> eventsToDelete, final int numOfAddresses, final Map<EventData, EventData> lxsxPairs) {
        this.events = new ArrayList<>(oldEvents.size());
        this.eventMap = new HashMap<>(getOptimalMapCapacity(oldEvents.size() - eventsToDelete.size()));
        this.threadEventsMap = new HashMap<>(optimalThreadMapCapacity);
        this.addressInits = new HashMap<>(getOptimalMapCapacity(numOfAddresses));
        this.readWriteMap = new HashMap<>();
        this.writeReadsMap = new HashMap<>();
        this.lxsxPairs = new HashMap<>();
        this.lastRegWriters = new HashMap<>();
        final int revisitWriteId = oldEvents.size() - 1;
        int id = -1;
        for (final EventData event : oldEvents) {
            if (!eventsToDelete.contains(event)) {
                final Event e = event.getEvent();
                final EventData copiedEvent = new EventData(e);
                eventMap.put(e, copiedEvent);
                copiedEvent.setReadFrom(event.getReadFrom());
                copiedEvent.setCoPredecessor(event.getCoPredecessor());
                copiedEvent.setCoSuccessor(event.getCoSuccessor());
                copiedEvent.setWasExecuted(event.wasExecuted());
                copiedEvent.setNonDet(event.isNonDet());
                copiedEvent.setAccessedAddress(event.getAccessedAddress());
                copiedEvent.setValue(event.getValue());
                copiedEvent.setId(++id);
                copiedEvent.setLocalId(event.getLocalId());
                events.add(copiedEvent);
                threadEventsMap.computeIfAbsent(e.getThread(), t -> new ArrayList<>()).add(copiedEvent);
                if (e instanceof Store && event.getCoPredecessor() == null && event.getId() != revisitWriteId) {
                    addressInits.put((BigInteger) event.getAccessedAddress(), copiedEvent);
                }
                if (e instanceof RegWriter regWriter) {
                    lastRegWriters.put(regWriter.getResultRegister(), copiedEvent);
                }
            }
        }
        for (final EventData event : events) {
            if (event.getReadFrom() != null) {
                final EventData newReadFrom = eventMap.get(event.getReadFrom().getEvent());
                event.setReadFrom(newReadFrom);
                readWriteMap.put(event, newReadFrom);
                writeReadsMap.computeIfAbsent(newReadFrom, rf -> new ArrayList<>()).add(event);
            }
            EventData coPredecessor = event.getCoPredecessor();
            while (coPredecessor != null) {
                final EventData copiedCoPredecessor = eventMap.get(coPredecessor.getEvent());
                if (copiedCoPredecessor != null) {
                    event.setCoPredecessor(copiedCoPredecessor);
                    break;
                }
                coPredecessor = coPredecessor.getCoPredecessor();
            }
            if (coPredecessor == null) {
                event.setCoPredecessor(null);
            }
            EventData coSuccessor = event.getCoSuccessor();
            while (coSuccessor != null) {
                final EventData copiedCoSuccessor = eventMap.get(coSuccessor.getEvent());
                if (copiedCoSuccessor != null) {
                    event.setCoSuccessor(copiedCoSuccessor);
                    break;
                }
                coSuccessor = coSuccessor.getCoSuccessor();
            }
            if (coSuccessor == null) {
                event.setCoSuccessor(null);
            }
        }
        for (final Map.Entry<EventData, EventData> lxsxPair : lxsxPairs.entrySet()) {
            final Event storeEvent = lxsxPair.getKey().getEvent();
            final Optional<EventData> store = getData(storeEvent);
            store.ifPresent(eventData -> this.lxsxPairs.put(eventData, getData(lxsxPair.getValue().getEvent())
                    .orElseThrow(() -> new IllegalArgumentException("Exclusive store " + storeEvent.getGlobalId() + ": " + storeEvent + " has not its exclusive load "
                            + lxsxPair.getValue().getEvent().getGlobalId() + ": " + lxsxPair.getValue().getEvent() + " in the new trace"))));
        }
    }

    public void addEvent(final EventData event) {
        events.add(event);
        final Event e = event.getEvent();
        eventMap.put(e, event);
        final List<EventData> threadEvents = threadEventsMap.computeIfAbsent(e.getThread(), thread -> new ArrayList<>());
        event.setLocalId(threadEvents.size());
        threadEvents.add(event);
        if (e instanceof Init) {
            addAddressInit(event);
        }
        if (e instanceof RegWriter regWriter) {
            lastRegWriters.put(regWriter.getResultRegister(), event);
        }
        final EventData readFrom = event.getReadFrom();
        if (readFrom != null) {
            readWriteMap.put(event, readFrom);
            writeReadsMap.computeIfAbsent(readFrom, rf -> new ArrayList<>()).add(event);
        }
        final EventData coPredecessor = event.getCoPredecessor();
        if (coPredecessor != null) {
            coPredecessor.setCoSuccessor(event);
        }
    }

    public void addAddressInit(final EventData addressWrite) {
        addressInits.put((BigInteger) addressWrite.getAccessedAddress(), addressWrite);
    }

    public List<EventData> rollback(final EventData backtrackingPoint) {
        final int backtrackingIndex = backtrackingPoint.getId();
        for (final Map.Entry<Thread, List<EventData>> thread : threadEventsMap.entrySet()) {
            final List<EventData> threadEvents = thread.getValue();
            int i;
            for (i = threadEvents.size() - 1; i >= 0; --i) {
                final EventData threadEvent = threadEvents.get(i);
                if (backtrackingIndex < threadEvent.getId()) {
                    final Event eventToRemove = threadEvent.getEvent();
                    final EventData readFrom = threadEvent.getReadFrom();
                    if (readFrom != null) {
                        readWriteMap.remove(threadEvent);
                        final List<EventData> readsForRfWrite = writeReadsMap.get(readFrom);
                        if (readsForRfWrite != null) {
                            readsForRfWrite.remove(threadEvent);
                        }
                    } else if (eventToRemove instanceof Store) {
                        if (threadEvent.getCoPredecessor() == null) {
                            final BigInteger address = (BigInteger) threadEvent.getAccessedAddress();
                            if (addressInits.get(address) == threadEvent) {
                                addressInits.remove(address);
                            }
                        }
                        writeReadsMap.remove(threadEvent);
                        if (eventToRemove instanceof RMWStoreExclusive) {
                            lxsxPairs.remove(threadEvent);
                        }
                    }
                    if (eventToRemove instanceof final RegWriter regWriter) {
                        lastRegWriters.remove(regWriter.getResultRegister());
                    }
                    eventMap.remove(eventToRemove);
                } else {
                    break;
                }
            }
            if (i <= 0) {
                threadEvents.clear();
            } else {
                threadEvents.subList(i + 1, threadEvents.size()).clear();
            }
        }
        return events.subList(backtrackingIndex + 1, events.size());
    }

    public void setReadFrom(final EventData read, final EventData newWrite, final EventData oldWrite) {
        if (oldWrite != null) {
            writeReadsMap.get(oldWrite).remove(read);
        }
        writeReadsMap.computeIfAbsent(newWrite, write -> new ArrayList<>()).add(read);
        readWriteMap.put(read, newWrite);
        read.setValue(newWrite.getValue());
        read.setReadFrom(newWrite);
    }

    public void addLxsxPair(final EventData store, final EventData load) {
        lxsxPairs.put(store, load);
    }

    public Optional<EventData> getLastAddedThreadEventData(final Thread thread) {
        final List<EventData> threadEvents = threadEventsMap.get(thread);
        if (threadEvents == null || threadEvents.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(threadEvents.get(threadEvents.size() - 1));
    }

    @Override
    public int size() {
        return events.size();
    }

    @Override
    public List<EventData> getElements() {
        return events;
    }

    public Map<Event, EventData> getEventMap() {
        return eventMap;
    }

    @Override
    public int getId(final Object obj) {
        if (obj instanceof final EventData event) {
            return event.getId();
        }
        throw new IllegalArgumentException("Excepted non-null object of class EventData");
    }

    @Override
    public EventData getObjectById(final int id) {
        try {
            return events.get(id);
        } catch (final IndexOutOfBoundsException ignored) {
            return null;
        }
    }

    public EventData getLast() {
        return events.get(events.size() - 1);
    }

    public EventData removeLast() {
        final EventData last = getLast();
        final Event lastEvent = last.getEvent();
        events.remove(last);
        eventMap.remove(lastEvent);
        threadEventsMap.get(lastEvent.getThread()).remove(last);
        if (last.getReadFrom() != null) {
            readWriteMap.remove(last);
            writeReadsMap.get(last.getReadFrom()).remove(last);
        }
        final EventData coPredecessor = last.getCoPredecessor();
        if (coPredecessor != null) {
            coPredecessor.setCoSuccessor(last.getCoSuccessor());
        }
        if (lastEvent instanceof RMWStoreExclusive) {
            lxsxPairs.remove(last);
        }
        return last;
    }

    public Map<Thread, List<EventData>> getThreadEventsMap() {
        return threadEventsMap;
    }

    public Collection<Thread> getThreads() {
        return threadEventsMap.keySet();
    }

    public Map<BigInteger, EventData> getAddressInits() {
        return addressInits;
    }

    public Map<EventData, EventData> getReadWriteMap() {
        return readWriteMap;
    }

    public Map<EventData, List<EventData>> getWriteReadsMap() {
        return writeReadsMap;
    }

    public Map<EventData, EventData> getLxsxPairs() {
        return lxsxPairs;
    }

    public Optional<EventData> getData(final Event e) {
        return Optional.ofNullable(eventMap.get(e));
    }

    public EventData getLastRegWriter(final Register reg) {
        final EventData alreadySeenRegWriter = lastRegWriters.get(reg);
        if (alreadySeenRegWriter != null) {
            return alreadySeenRegWriter;
        }
        final EventData lastRegWriter = getLastRegWriter(reg, null);
        lastRegWriters.put(reg, lastRegWriter);
        return lastRegWriter;
    }

    public EventData getLastRegWriter(final Register reg, final EventData forEvent) {
        final EventData alreadySeenRegWriter = lastRegWriters.get(reg);
        if (alreadySeenRegWriter != null) {
            return alreadySeenRegWriter;
        }
        final List<EventData> threadEvents = threadEventsMap.get(reg.getThread());
        final int eventLocalId = forEvent == null ? -1 : forEvent.getLocalId();
        final int startIdExclusive = eventLocalId == -1 ? threadEvents.size() : eventLocalId;
        for (int i = startIdExclusive - 1; i >= 0; --i) {
            final EventData event = threadEvents.get(i);
            if (event.getEvent() instanceof RegWriter writer && writer.getResultRegister() == reg) {
                return event;
            }
        }
        return null;
    }

    public static void setThreadCount(final int threadCount) {
        optimalThreadMapCapacity = getOptimalMapCapacity(threadCount);
    }

    @Override
    public String toString() {
        return toString(getElements());
    }

    public String toString(final Filter eventFilter) {
        return toString(getElements(), eventFilter);
    }

    public static String toString(final Collection<EventData> events, final Filter eventFilter) {
        return toString(events.stream()
                .filter(event -> eventFilter.apply(event.getEvent()))
                .toList());
    }

    public static String toString(final Collection<EventData> events) {
        final StringBuilder builder = new StringBuilder();
        Event pred = null;
        for (EventData eventData : events) {
            final Event event = eventData.getEvent();
            if ((event instanceof ThreadStart && event.getSuccessor() instanceof Init) || (event.getFunction().getExit() == event && event.getPredecessor() instanceof Init)) {
                continue;
            }
            builder.append('\n');
            if (pred != null && pred.getThread() != event.getThread() && !(event instanceof Init)) {
                builder.append("\n");
            }
            builder.append(event.getThread().getId())
                    .append(": ");
            builder.append(event.getGlobalId())
                    .append(": ");
            builder.append(eventData.getId())
                    .append(": ");
            if (eventData.isMemoryEvent()) {
                builder.append(eventData.getAccessedAddress())
                        .append(": ");
            }
            builder.append(event.getClass().getSimpleName())
                    .append(": ");
            builder.append(event);
            if (eventData.getReadFrom() != null) {
                final Event readFrom = eventData.getReadFrom().getEvent();
                builder.append(" reads from ")
                        .append(readFrom.getGlobalId())
                        .append(": ")
                        .append(readFrom);
            }
            if (eventData.getCoPredecessor() != null) {
                final Event coPred = eventData.getCoPredecessor().getEvent();
                builder.append(", writes after ")
                        .append(coPred.getGlobalId())
                        .append(": ")
                        .append(coPred);
            }
            if (eventData.getCoSuccessor() != null) {
                final Event coSuc = eventData.getCoSuccessor().getEvent();
                builder.append(", writes before ")
                        .append(coSuc.getGlobalId())
                        .append(": ")
                        .append(coSuc);
            }
            if (eventData.getCoherenceIndex() != Integer.MIN_VALUE) {
                builder.append(", coherence index: ")
                        .append(eventData.getCoherenceIndex());
            }
            pred = event;
        }
        return builder.toString();
    }

    public String toRelevantEventsString() {
        return toRelevantEventsString(getElements());
    }

    public static String toRelevantEventsString(final Collection<EventData> events) {
        final List<EventData> filteredEvents = new ArrayList<>();
        for (final EventData eventData : events) {
            final Event event = eventData.getEvent();
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
            if (event instanceof final RegWriter writer && isSynthetic(writer.getResultRegister().getName())) {
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

    public String getRelationData() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Thread events map: ");
        for (final Map.Entry<Thread, List<EventData>> entry : threadEventsMap.entrySet()) {
            sb.append('\n').append(entry.getKey()).append(": [");
            for (final EventData event : entry.getValue()) {
                sb.append(event.getId()).append(": ").append(event.getEvent()).append(", ");
            }
            sb.append("]");
        }
        sb.append('\n').append("Address inits: [");
        for (final EventData addrInit : addressInits.values()) {
            sb.append('\n').append(addrInit.getEvent()).append(": [");
            for (final EventData event : addrInit.getCoSuccessors()) {
                sb.append(event.getEvent()).append(", ");
            }
            sb.append("]");
        }
        sb.append('\n').append("Read-write map: ");
        for (final Map.Entry<EventData, EventData> entry : readWriteMap.entrySet()) {
            sb.append('\n').append(entry.getKey().getEvent()).append(": ");
            sb.append(entry.getValue().getEvent());
        }
        sb.append('\n').append("Write-reads map: ");
        for (final Map.Entry<EventData, List<EventData>> entry : writeReadsMap.entrySet()) {
            sb.append('\n').append(entry.getKey().getEvent()).append(": [");
            for (final EventData event : entry.getValue()) {
                sb.append(event.getEvent()).append(", ");
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private static boolean isSynthetic(final String name) {
        return name.startsWith("__");
    }

    static boolean isThreadEndStore(final EventData store) {
        return store.getEvent() instanceof Store && store.getEvent().getSuccessor() instanceof final Label label && label.getThread().getEntry().isSpawned()
                && label.getName().startsWith("END_OF_T");
    }
}
