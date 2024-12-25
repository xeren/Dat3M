package com.dat3m.dartagnan.solver.caat4wmm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Init;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.core.Store;
import com.dat3m.dartagnan.program.event.core.threading.ThreadCreate;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.verification.model.EventData;

import static java.util.stream.Collectors.toList;

public class GenMCEventDomain implements Domain<EventData> {

    private final List<EventData> events;
    private final Map<Thread, List<EventData>> threadEventsMap;
    private final List<EventData> addressInits;
    private final Map<EventData, EventData> readWriteMap;
    private final Map<EventData, List<EventData>> writeReadsMap;

    public GenMCEventDomain() {
        this.events = new ArrayList<>();
        this.threadEventsMap = new HashMap<>();
        this.addressInits = new ArrayList<>();
        this.readWriteMap = new HashMap<>();
        this.writeReadsMap = new HashMap<>();
    }

    public GenMCEventDomain(Collection<EventData> events, Map<Thread, List<EventData>> threadEventsMap, List<EventData> addressInits, Map<EventData, EventData> readWriteMap,
            Map<EventData, List<EventData>> writeReadsMap) {
        this.events = new ArrayList<>(events);
        this.threadEventsMap = new HashMap<>();
        for (Map.Entry<Thread, List<EventData>> threadEvents : threadEventsMap.entrySet()) {
            List<EventData> newThreadEvents = threadEvents.getValue().stream()
                    .map(e -> getData(e.getEvent()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(toList());;
            if (!newThreadEvents.isEmpty()) {
                this.threadEventsMap.put(threadEvents.getKey(), newThreadEvents);
            }
        }
        this.addressInits = addressInits.stream()
                .map(e -> getData(e.getEvent()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
        this.readWriteMap = new HashMap<>();
        for (EventData read : readWriteMap.keySet()) {
            Optional<EventData> readData = getData(read.getEvent());
            if (readData.isPresent()) {
                this.readWriteMap.put(readData.get(), readData.get().getReadFrom());
            }
        }
        this.writeReadsMap = new HashMap<>();
        for (Map.Entry<EventData, List<EventData>> writeReads : writeReadsMap.entrySet()) {
            Optional<EventData> writeData = getData(writeReads.getKey().getEvent());
            if (writeData.isPresent()) {
                List<EventData> newWriteReads = writeReads.getValue().stream()
                        .map(e -> getData(e.getEvent()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(toList());
                this.writeReadsMap.put(writeData.get(), newWriteReads);
            }
        }
    }

    public void addEvent(EventData event) {
        events.add(event);
        threadEventsMap.computeIfAbsent(event.getEvent().getThread(), thread -> new ArrayList<>()).add(event);
        if (event.getEvent() instanceof Init) {
            addressInits.add(event);
        }
        EventData readFrom = event.getReadFrom();
        if (readFrom != null) {
            readWriteMap.put(event, readFrom);
            writeReadsMap.computeIfAbsent(readFrom, rf -> new ArrayList<>()).add(event);
        }
        EventData coPredecessor = event.getCoPredecessor();
        if (coPredecessor != null) {
            coPredecessor.setCoSuccessor(event);
        }
    }

    public void addAddressInit(EventData addressWrite) {
        addressInits.add(addressWrite);
    }

    public void rollback(EventData backtrackingPoint) {
        int backtrackingIndex = events.indexOf(backtrackingPoint);
        List<EventData> eventsToRemove = events.subList(backtrackingIndex + 1, events.size());
        for (EventData eventToRemove : eventsToRemove) {
            threadEventsMap.get(eventToRemove.getEvent().getThread()).remove(eventToRemove);
            addressInits.remove(eventToRemove);
            if (eventToRemove.isRead()) {
                readWriteMap.remove(eventToRemove);
                List<EventData> readsForRfWrite = writeReadsMap.get(eventToRemove.getReadFrom());
                if (readsForRfWrite != null) {
                    readsForRfWrite.remove(eventToRemove);
                }
            } else if (eventToRemove.isWrite()) {
                writeReadsMap.remove(eventToRemove);
            }
        }
        eventsToRemove.clear();
    }

    public void setReadFrom(EventData read, EventData newWrite, EventData oldWrite) {
        if (oldWrite != null) {
            writeReadsMap.get(oldWrite).remove(read);
        }
        writeReadsMap.computeIfAbsent(newWrite, write -> new ArrayList<>()).add(read);
        readWriteMap.put(read, newWrite);
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

    public int getLocalId(EventData event) {
        return threadEventsMap.get(event.getEvent().getThread()).indexOf(event);
    }

    public EventData getLast() {
        return events.get(events.size() - 1);
    }

    public EventData removeLast() {
        EventData last = getLast();
        events.remove(last);
        threadEventsMap.get(last.getEvent().getThread()).remove(last);
        if (last.getReadFrom() != null) {
            readWriteMap.remove(last);
            writeReadsMap.get(last.getReadFrom()).remove(last);
        }
        EventData coPredecessor = last.getCoPredecessor();
        if (coPredecessor != null) {
            coPredecessor.setCoSuccessor(last.getCoSuccessor());
        }
        return last;
    }

    public Map<Thread, List<EventData>> getThreadEventsMap() {
        return threadEventsMap;
    }

    public Collection<Thread> getThreads() {
        return threadEventsMap.keySet();
    }

    public List<EventData> getAddressInits() {
        return addressInits;
    }

    public Map<EventData, EventData> getReadWriteMap() {
        return readWriteMap;
    }

    public Map<EventData, List<EventData>> getWriteReadsMap() {
        return writeReadsMap;
    }

    public Optional<EventData> getData(Event e) {
        return getData(getElements(), e);
    }

    public static Optional<EventData> getData(List<EventData> events, Event e) {
        return events.stream()
                .filter(event -> event.getEvent() == e)
                .findAny();
    }

    public EventData getLastRegWriter(Register reg, Event forEvent) {
         return getElements().stream()
                    .filter(event -> event.getEvent() instanceof RegWriter writer && writer.getResultRegister() == reg && writer != forEvent)
                    .max(Comparator.comparingInt(EventData::getId))
                    .orElse(null);
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
            Event event = eventData.getEvent();
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
                Event readFrom = eventData.getReadFrom().getEvent();
                builder.append(" reads from ")
                        .append(readFrom.getGlobalId())
                        .append(": ")
                        .append(readFrom);
            }
            if (eventData.getCoPredecessor() != null) {
                Event coPred = eventData.getCoPredecessor().getEvent();
                builder.append(", writes after ")
                        .append(coPred.getGlobalId())
                        .append(": ")
                        .append(coPred);
            }
            if (eventData.getCoSuccessor() != null) {
                Event coSuc = eventData.getCoSuccessor().getEvent();
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

    public String getRelationData() {
        StringBuilder sb = new StringBuilder();
        sb.append("Thread events map: ");
        for (Map.Entry<Thread, List<EventData>> entry : threadEventsMap.entrySet()) {
            sb.append('\n').append(entry.getKey()).append(": [");
            for (EventData event : entry.getValue()) {
                sb.append(event.getId()).append(": ").append(event.getEvent()).append(", ");
            }
            sb.append("]");
        }
        sb.append('\n').append("Address inits: [");
        for (EventData addrInit : addressInits) {
            sb.append('\n').append(addrInit.getEvent()).append(": [");
            for (EventData event : addrInit.getCoSuccessors()) {
                sb.append(event.getEvent()).append(", ");
            }
            sb.append("]");
        }
        sb.append('\n').append("Read-write map: ");
        for (Map.Entry<EventData, EventData> entry : readWriteMap.entrySet()) {
            sb.append('\n').append(entry.getKey().getEvent()).append(": ");
            sb.append(entry.getValue().getEvent());
        }
        sb.append('\n').append("Write-reads map: ");
        for (Map.Entry<EventData, List<EventData>> entry : writeReadsMap.entrySet()) {
            sb.append('\n').append(entry.getKey().getEvent()).append(": [");
            for (EventData event : entry.getValue()) {
                sb.append(event.getEvent()).append(", ");
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private static boolean isSynthetic(String name) {
        return name.startsWith("__");
    }

    public boolean isFinished(Program program) {
        if (program.getFormat() == Program.SourceLanguage.LITMUS) {
            return true;
        }
        List<Event> mainThreadEvents = program.getThreads().get(0).getEvents();
        Event mainThreadEndLabel = mainThreadEvents.get(mainThreadEvents.size() - 1);
        for (EventData event : events) {
            if (event.getEvent() == mainThreadEndLabel.getPredecessor()) {
                return true;
            }
        }
        return false;
    }

    static boolean isThreadEndStore(EventData store) {
        return store.getEvent() instanceof Store && store.getEvent().getSuccessor() instanceof Label label && label.getThread().getEntry().isSpawned()
                && label.getName().startsWith("END_OF_T");
    }
}
