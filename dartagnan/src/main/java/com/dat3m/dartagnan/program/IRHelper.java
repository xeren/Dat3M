package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.expression.processing.ExprTransformer;
import com.dat3m.dartagnan.program.event.AbstractEvent;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.EventUser;
import com.dat3m.dartagnan.program.event.RegReader;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Init;
import com.dat3m.dartagnan.program.event.functions.AbortIf;
import com.dat3m.dartagnan.program.event.functions.Return;
import com.dat3m.dartagnan.program.event.lang.llvm.LlvmCmpXchg;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.Maps;

import java.util.*;
import java.util.function.Consumer;

import static com.dat3m.dartagnan.program.event.EventFactory.eventSequence;

public class IRHelper {

    private IRHelper() {}

    public static boolean isInitThread(Thread thread) {
        return thread.getEntry().getSuccessor() instanceof Init;
    }

    public static boolean isBackJump(CondJump jump) {
        return jump.getLocalId() > jump.getLabel().getLocalId();
    }

    /*
        Returns true if the syntactic successor of <e> (e.getSuccessor()) is not (generally) a semantic successor,
        because <e> always jumps/branches/terminates etc.
    */
    public static boolean isAlwaysBranching(Event e) {
        return e instanceof Return
                || e instanceof AbortIf abort && abort.getCondition() instanceof BoolLiteral b && b.getValue()
                || e instanceof CondJump jump && jump.isGoto();
    }

    public static void insertAfter(Event position, Event toBeInserted) {
        insertAround(position, null, eventSequence(position, toBeInserted));
    }

    public static void insertAfter(Event position, Event firstToBeInserted, Event... restToBeInserted) {
        insertAround(position, null, eventSequence(position, firstToBeInserted, restToBeInserted));
    }

    public static void insertAfter(Event position, Iterable<? extends Event> toBeInserted) {
        insertAround(position, null, eventSequence(position, toBeInserted));
    }

    public static void insertAfter(Event position, Object firstToBeInserted, Object... restToBeInserted) {
        insertAround(position, null, eventSequence(position, firstToBeInserted, restToBeInserted));
    }

    public static void insertBefore(Event position, Event toBeInserted) {
        insertAround(position, eventSequence(toBeInserted, position), null);
    }

    public static void insertBefore(Event position, Event first, Event... restToBeInserted) {
        insertAround(position, eventSequence(first, restToBeInserted, position), null);
    }

    public static void insertBefore(Event position, Iterable<? extends Event> toBeInserted) {
        insertAround(position, eventSequence(toBeInserted, position), null);
    }

    public static void insertBefore(Event position, Object firstToBeInserted, Object... restToBeInserted) {
        insertAround(position, eventSequence(firstToBeInserted, restToBeInserted, position), null);
    }

    /**
     *
     * @param position Event already inside a program.
     * @param before Nullable, finite sequence of events to be inserted before {@code position} in the specified order.
     * @param after Nullable, finite sequence of events to be inserted after {@code position} in the specified order.
     */
    @SuppressWarnings("deprecation")
    public static void insertAround(Event position, Iterable<? extends Event> before, Iterable<? extends Event> after) {
        AbstractEvent.insertAround(position, before, after);
    }

    public static boolean tryDelete(Event toBeDeleted) {
        return replace(Map.of(toBeDeleted, List.of()), false).isEmpty();
    }

    public static <E extends Event> Set<E> bulkDelete(Set<E> toBeDeleted) {
        return replace(Maps.asMap(toBeDeleted, k -> List.of()), false);
    }

    public static void replaceWithMetadata(Event event, Event... newEvents) {
        replaceWithMetadata(event, eventSequence(newEvents));
    }

    public static void replaceWithMetadata(Event event, Object... newEvents) {
        replaceWithMetadata(event, eventSequence(newEvents));
    }

    public static void replaceWithMetadata(Event event, Iterable<? extends Event> replacement) {
        replace(event, replacement);
        replacement.forEach(ev -> ev.copyAllMetadataFrom(event));
    }

    public static void replace(Event old, Event... newEvents) {
        replace(Map.of(old, eventSequence(newEvents)), true);
    }

    public static void replace(Event old, Object... newEvents) {
        replace(Map.of(old, eventSequence(newEvents)), true);
    }

    public static void replace(Event old, Iterable<? extends Event> newEvents) {
        replace(Map.of(old, eventSequence(newEvents)), true);
    }

    public static <E extends Event> Set<E> replace(Map<E, ? extends List<? extends Event>> map, boolean force) {
        return replace(map, Map.of(), force);
    }

    /**
     * Substitutes each specified event with an associated sequence.
     * @param map Lists the events to be replaced, as well as the sequences to replace them with.
     * Values shall be distinct sets and may only intersect with {@code map.keySet()} in the associated key.
     * @param dependencies Each entry states that an event can only be removed if all specified events are removed.
     * Missing entries are equivalent to an empty value.
     * Keys not present in {@code map.keySet()} are ignored.
     * @param force Throw if at least one event cannot be replaced.
     * @return All events that could not be replaced.
     * @throws NullPointerException If {@code map == null || dependencies == null}.
     * @throws IllegalStateException If {@code force == true} and some event cannot be replaced.
     */
    @SuppressWarnings("deprecation")
    public static <E extends Event> Set<E> replace(
            Map<E, ? extends List<? extends Event>> map,
            Map<E, ? extends Collection<? extends E>> dependencies,
            boolean force) {
        return AbstractEvent.replace(map, dependencies, force);
    }

    public static List<Event> getEventsFromTo(Event from, Event to, boolean endInclusive) {
        Preconditions.checkArgument(from.getFunction() == to.getFunction());
        final List<Event> events = new ArrayList<>();
        Event cur = from;
        do {
            final boolean endReached = (cur == to);
            if (!endReached || endInclusive) {
                events.add(cur);
            }
            if (endReached) {
                break;
            }
            cur = cur.getSuccessor();
        } while (cur != null);
        Verify.verify(cur != null, "Event '{}' is not before '{}'", from, to);

        return events;
    }

    public static List<Event> copyEvents(Collection<? extends Event> events,
                                         Consumer<Event> copyUpdater,
                                         Map<Event, Event> copyContext) {
        final List<Event> copies = new ArrayList<>();
        for (Event e : events) {
            final Event copy = e.getCopy();
            copyUpdater.accept(copy);
            copies.add(copy);
            copyContext.put(e, copy);
        }

        copies.stream().filter(EventUser.class::isInstance).map(EventUser.class::cast)
                .forEach(user -> user.updateReferences(copyContext));

        return copies;
    }

    public static List<Event> copyPath(Event from, Event toExclusive,
                                       Consumer<Event> copyUpdater,
                                       Map<Event, Event> copyContext) {
        return copyEvents(getEventsFromTo(from, toExclusive, false), copyUpdater, copyContext);
    }

    public static Consumer<Event> makeRegisterReplacer(Map<Register, Register> regReplacer) {
        final ExprTransformer regSubstitutor =  new ExprTransformer() {
            @Override
            public Expression visitRegister(Register reg) {
                return regReplacer.getOrDefault(reg, reg);
            }
        };
        return event -> {
            if (event instanceof RegReader reader) {
                reader.transformExpressions(regSubstitutor);
            }
            if (event instanceof LlvmCmpXchg xchg) {
                xchg.setStructRegister(0, (Register)xchg.getStructRegister(0).accept(regSubstitutor));
                xchg.setStructRegister(1, (Register)xchg.getStructRegister(1).accept(regSubstitutor));
            } else if (event instanceof RegWriter regWriter) {
                regWriter.setResultRegister((Register) regWriter.getResultRegister().accept(regSubstitutor));
            }
        };
    }

    public static Map<Register, Register> copyOverRegisters(Iterable<Register> toCopy, Function target,
                                                            java.util.function.Function<Register, String> registerRenaming,
                                                            boolean guaranteeFreshRegisters) {
        final Map<Register, Register> registerMap = new HashMap<>();
        for (Register reg : toCopy) {
            final String name = registerRenaming.apply(reg);
            final Register copiedReg = guaranteeFreshRegisters ?
                    target.newRegister(name, reg.getType())
                    : target.getOrNewRegister(name, reg.getType());
            registerMap.put(reg, copiedReg);
        }
        return registerMap;
    }
}
