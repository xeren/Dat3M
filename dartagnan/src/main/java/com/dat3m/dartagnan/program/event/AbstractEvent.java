package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.metadata.CustomPrinting;
import com.dat3m.dartagnan.program.event.metadata.Metadata;
import com.dat3m.dartagnan.program.event.metadata.MetadataMap;
import com.dat3m.dartagnan.verification.Context;
import com.google.common.base.Preconditions;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.*;

public abstract class AbstractEvent implements Event {

    private final MetadataMap metadataMap = new MetadataMap();
    private final TagSet tags;
    private final Set<EventUser> currentUsers = new HashSet<>();
    // These ids are dynamically changing during processing.
    private transient int globalId = -1; // (Global) ID within a program
    private transient int localId = -1; // (Local) ID within a function

    private transient Function function; // The function this event belongs to
    private transient AbstractEvent successor;
    private transient AbstractEvent predecessor;

    protected AbstractEvent() {
        tags = new TagSet();
    }

    protected AbstractEvent(AbstractEvent other) {
        copyAllMetadataFrom(other);
        this.tags = other.tags.copy();
    }

    @Override
    public int getGlobalId() { return globalId; }
    @Override
    public void setGlobalId(int id) { this.globalId = id; }

    @Override
    public int getLocalId() { return localId; }
    @Override
    public void setLocalId(int id) { this.localId = id; }

    @Override
    public Function getFunction() { return function; }
    @Override
    public void setFunction(Function function) {
        this.function = Preconditions.checkNotNull(function);
    }

    @Override
    public Thread getThread() {
        return getFunction() instanceof Thread ? (Thread) getFunction() : null;
    }

    // ============================================ Users ============================================

    @Override
    public Set<EventUser> getUsers() { return this.currentUsers; }

    @Override
    public boolean registerUser(EventUser user) { return this.currentUsers.add(user); }

    @Override
    public boolean removeUser(EventUser user) { return this.currentUsers.remove(user); }

    @Override
    public void replaceAllUsages(Event replacement) {
        final Map<Event, Event> replacementMap = Map.of(this, replacement);
        List.copyOf(getUsers()).forEach(e -> e.updateReferences(replacementMap));
    }

    // ============================================ Metadata ============================================

    @Override
    public boolean hasMetadata(Class<? extends Metadata> metadataClass) { return metadataMap.contains(metadataClass); }
    @Override
    public <T extends Metadata> T getMetadata(Class<T> metadataClass) { return metadataMap.get(metadataClass); }
    @Override
    public <T extends Metadata> T setMetadata(T metadata) { return metadataMap.put(metadata); }

    @Override
    public void copyAllMetadataFrom(Event other) {
        ((AbstractEvent) other).metadataMap.getAllMetadata().forEach(this.metadataMap::put);
    }

    @Override
    public void copyMetadataFrom(Event other, Class<? extends Metadata> metadataClass) {
        Metadata metadata = other.getMetadata(metadataClass);
        if (metadata == null) {
            this.metadataMap.remove(metadataClass);
        } else {
            this.setMetadata(metadata);
        }
    }

    @Override
    public boolean hasEqualMetadata(Event other, Class<? extends Metadata> metadataClass) {
        return Objects.equals(getMetadata(metadataClass), other.getMetadata(metadataClass));
    }

    // ===============================================================================================

    // ============================================ Tags =============================================

    // The set of tags should not be modified directly.
    @Override
    public Set<String> getTags() { return tags; }
    @Override
    public boolean hasTag(String tag) { return tag != null && tags.contains(tag); }
    @Override
    public void addTags(Collection<? extends String> tags) { this.tags.addAll(tags); }
    @Override
    public void addTags(String... tags) { addTags(Arrays.asList(tags)); }
    @Override
    public void removeTags(Collection<? extends String> tags) { this.tags.removeAll(tags); }
    @Override
    public void removeTags(String... tags) { removeTags(Arrays.asList(tags)); }

    // ===============================================================================================

    // ======================================== Control-flow =========================================

    @Override
    public Event getSuccessor() { return successor; }
    @Override
    public void setSuccessor(Event ev) { successor = (AbstractEvent) ev; }

    @Override
    public Event getPredecessor() { return predecessor; }
    @Override
    public void setPredecessor(Event ev) { predecessor = (AbstractEvent) ev; }

    @Override
    public final List<Event> getSuccessors() {
        List<Event> events = new ArrayList<>();
        Event cur = this;
        while (cur != null) {
            events.add(cur);
            cur = cur.getSuccessor();
        }
        return events;
    }

    @Override
    public final List<Event> getPredecessors() {
        List<Event> events = new ArrayList<>();
        Event cur = this;
        while (cur != null) {
            events.add(cur);
            cur = cur.getPredecessor();
        }
        return events;
    }

    /*
        Detaches this event from the control-flow graph.
        This does not properly delete the event, and it may be reinserted elsewhere.
        TODO: We should handle the edge-case where a function becomes empty after detaching.
     */
    private void detach() {
        Preconditions.checkState(function == null || function.getEntry() != function.getExit(),
                "Cannot detach the only event %s of function %s", this, getFunction());
        if (this.predecessor != null) {
            this.predecessor.successor = successor;
        }
        if (this.successor != null) {
            this.successor.predecessor = predecessor;
        }
        if (function != null && this == function.getEntry()) {
            function.updateEntry(this.successor);
        }
        if (function != null && this == function.getExit()) {
            function.updateExit(this.predecessor);
        }

        this.function = null;
        this.predecessor = null;
        this.successor = null;
    }

    /**
     * @deprecated Only to be used directly by {@link com.dat3m.dartagnan.program.IRHelper IRHelper}.
     */
    @Deprecated
    public static <K extends Event, E extends Event, V extends List<E>> Set<K> replace(
            Map<K, V> map, Map<K, ? extends Collection<? extends K>> dependencies, boolean force) {
        Preconditions.checkNotNull(map, "Missing substitution map.");
        Preconditions.checkNotNull(dependencies, "Missing dependency map.");
        final var toBeRemoved = new HashSet<K>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (!entry.getValue().contains(entry.getKey())) {
                toBeRemoved.add(entry.getKey());
            }
        }
        // An event can only be removed, if all users can be removed as well.
        final var failed = new HashSet<K>();
        while (true) {
            final List<K> news = toBeRemoved.stream()
                    .filter(k -> !canDelete(k, dependencies.get(k), toBeRemoved))
                    .toList();
            if (news.isEmpty()) {
                break;
            }
            failed.addAll(news);
            toBeRemoved.removeAll(news);
        }
        // Assert that replacement is not blocked.
        Preconditions.checkState(!force || failed.isEmpty(), "Unable to remove events %s.", failed);
        // Assume that all values are distinct sets and that no replacements contain other keys.
        // Remove usages.
        for (K event : toBeRemoved) {
            if (event instanceof EventUser user && user instanceof AbstractEvent) {
                user.getReferencedEvents().forEach(e -> e.removeUser(user));
            }
        }
        // Perform replacement.
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof AbstractEvent event)) {
                continue;
            }
            List<E> replacement = entry.getValue();
            // Assume replacement consists of instances of AbstractEvent.
            final int index = replacement.indexOf(event);
            assert index != -1 || event.currentUsers.isEmpty();
            final List<E> before = index == -1 ? replacement : replacement.subList(0, index);
            event.insertBefore(before);
            if (index != -1) {
                event.insertAfter(replacement.subList(index + 1, replacement.size()));
            } else if (toBeRemoved.contains(event)) {
                // Assume no referenced event has this user.
                event.detach();
            }
        }
        return failed;
    }

    private static boolean canDelete(Event event, Collection<?> dependencies, Collection<?> intention) {
        // Never try to remove custom events.
        if (!(event instanceof AbstractEvent e)) {
            return false;
        }
        // Ignore events that stay.
        if (!intention.contains(e)) {
            return true;
        }
        // Fail to delete if dependencies remain.
        if (dependencies != null && !intention.containsAll(dependencies)) {
            return false;
        }
        // Fail to delete if users remain.
        return intention.containsAll(e.currentUsers);
    }

    /**
     * @deprecated Only to be used directly by {@link com.dat3m.dartagnan.program.IRHelper IRHelper}.
     */
    @Deprecated
    public static void insertAround(Event position, Iterable<? extends Event> before, Iterable<? extends Event> after) {
        Preconditions.checkArgument(position instanceof AbstractEvent,
                "Unable to insert at custom event %s.", position);
        ((AbstractEvent) position).insertBefore(before);
        ((AbstractEvent) position).insertAfter(after);
    }

    private void insertAfter(Iterable<? extends Event> toBeInserted) {
        if (toBeInserted == null) { return; }
        final AbstractEvent suc = successor;
        AbstractEvent cur = this;
        for (Event next : toBeInserted) {
            insertBetween((AbstractEvent) next, function, cur, suc);
            cur = (AbstractEvent) next;
        }
    }

    private void insertBefore(Iterable<? extends Event> toBeInserted) {
        if (toBeInserted == null) { return;}
        for (Event next : toBeInserted) {
            insertBetween((AbstractEvent) next, function, this.predecessor, this);
        }
    }

    private static void insertBetween(AbstractEvent toBeInserted, Function func, AbstractEvent pred, AbstractEvent succ) {
        assert (pred == null || pred.successor == succ) && (succ == null || succ.predecessor == pred);
        assert (toBeInserted != pred && toBeInserted != succ);
        toBeInserted.detach();
        toBeInserted.function = func;
        toBeInserted.predecessor = pred;
        toBeInserted.successor = succ;

        if (pred != null) {
            pred.successor = toBeInserted;
        } else if (func != null) {
            func.updateEntry(toBeInserted);
        }

        if (succ != null) {
            succ.predecessor = toBeInserted;
        } else if (func != null) {
            func.updateExit(toBeInserted);
        }

    }

    // ===============================================================================================

    protected abstract String defaultString();

    @Override
    public final String toString() {
        final CustomPrinting stringProvider = getMetadata(CustomPrinting.class);
        final Optional<String> customString = Optional.ofNullable(stringProvider).flatMap(o -> o.stringify(this));
        return customString.orElseGet(this::defaultString);
    }

    // ======================================== Miscellaneous ========================================

    @Override
    public int compareTo(Event e) {
        if (e == this) {
            return 0;
        }
        int result = Integer.compare(this.getGlobalId(), e.getGlobalId());
        if (result == 0 && this.getGlobalId() != -1) {
            final String error = String.format("Events %s and %s are different but have the same global id %d",
                    this, e, e.getGlobalId());
            throw new IllegalStateException(error);
        }
        result = (result != 0) ? result :  (Integer.compare(this.getLocalId(), e.getLocalId()));
        if (result == 0) {
            final String error = String.format("Events %s and %s are different but have the same local id %d",
                    this, e, e.getLocalId());
            throw new IllegalStateException(error);
        }
        return result;
    }

    @Override
    public <T> T accept(EventVisitor<T> visitor) {
        return visitor.visitEvent(this);
    }

    @Override
    public void runLocalAnalysis(Program program, Context context) { }

    // This method needs to get overwritten for conditional events.
    @Override
    public boolean cfImpliesExec() {
        return !(this instanceof BlockingEvent);
    }

    @Override
    public BooleanFormula encodeExec(EncodingContext ctx) {
        return ctx.getBooleanFormulaManager().makeTrue();
    }
}