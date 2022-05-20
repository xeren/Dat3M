package com.dat3m.dartagnan.wmm;

import com.dat3m.dartagnan.program.event.core.Event;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * Used in the relation analysis.
 * Instances of this class are matched to a relation in a verification task.
 * Describes a finite mapping from events of a program to one of the following elements:
 * <ul>
 * <li>If the pair is not contained in this collection after the analysis ended,
 * then the relationship cannot exist in the memory model.
 * <li>If the contained pair is {@link #isTrue(Event,Event) true},
 * then all executions that are to be modelled by the verifier enable the relationship.
 * <li>If the contained pair is {@link #isFalse(Event,Event) false},
 * then all verified executions disable the relationship.
 * <li>If it is neither, the verifier has to describe the relationship with a non-trivial value.
 * <li>If it is both, no verified execution executes both events.
 * </ul>
 * Instances should be monotonic:
 * They keep all pairs added to them to the end of their lifetime.
 * Also, pairs cannot be unmarked as true or false.
 */
public interface Knowledge {

    enum Value {NONE,CARE,FALSE,TRUE,BOTH}

    /**
     * Retrieves the knowledge about a certain relationship in the verification task.
     * @param first
     * Element of the verified program.
     * @param second
     * Element of the verified program.
     * @return
     * {@link Value#BOTH} if {@code first} or {@code second} have to miss in each relevant execution.
     * {@link Value#TRUE} if the relationship has to be enabled in each relevant executions.
     * {@link Value#FALSE} if the relationship has to be disabled in each relevant executions.
     * {@link Value#NONE} if the relationship is contained, but no further information is available.
     * {@code null} if the relationship is not contained.
     */
    Value get(Event first, Event second);

    /**
     * @param first
     * Event of the verified program.
     * @param second
     * Another event of the verified program.
     * @return
     * The pair is contained in this graph.
     */
    default boolean contains(Event first, Event second) {
        return get(first,second) != null;
    }

    /**
     * @param first
     * Event of the verified program.
     * @param second
     * Another event of the verified program.
     * @return
     * The pair is contained in this graph and also disabled in all relevant executions.
     */
    default boolean isFalse(Event first, Event second) {
        Value v = get(first,second);
        return v == Value.FALSE || v == Value.BOTH;
    }

    /**
     * @param first
     * Event of the verified program.
     * @param second
     * Another event of the verified program.
     * @return
     * The pair is contained in this graph and also enabled in all relevant executions.
     */
    default boolean isTrue(Event first, Event second) {
        Value v = get(first,second);
        return v == Value.FALSE || v == Value.BOTH;
    }

    /**
     * @param first
     * Event of the verified program.
     * @param second
     * Another event of the verified program.
     * @return
     * The pair is contained in this graph and marked as relevant to the consistency property.
     */
    default boolean isActive(Event first, Event second) {
        return get(first,second) == Value.CARE;
    }

    /**
     * @return
     * Set of events that participate in the relationship with outgoing edges.
     */
    Collection<Event> allFirst();

    /**
     * @param first
     * Event of the verified program.
     * @return
     * Set of events that have incoming edges from {@code first}.
     */
    Collection<Event> byFirst(Event first);

    /**
     * @return
     * Set of events that participate in the relationship with incoming edges.
     */
    Collection<Event> allSecond();

    /**
     * @param second
     * Event of the verified program.
     * @return
     * Set of events that have outgoing edges to {@code second}.
     */
    Collection<Event> bySecond(Event second);

    /**
     * Marks a relationship as possible.
     * @param first
     * Event of the verified program.
     * @param second
     * Another event of the verified program.
     * @return
     * The knowledge increased by this operation.
     */
    boolean add(Event first, Event second);

    /**
     * Marks a relationship as true if both events are executed.
     * If not already, it is {@link #add(Event,Event) added}.
     * @param first
     * Event of the verified program.
     * @param second
     * Another event of the verified program.
     * @return
     * The knowledge increased by this operation.
     */
    boolean enable(Event first, Event second);

    /**
     * Marks a relationship as false for each relevant execution.
     * If not already, it is {@link #add(Event,Event) added}.
     * @param first
     * Event of the verified program.
     * @param second
     * Another event of the verified program.
     * @return
     * The knowledge increased by this operation.
     */
    boolean disable(Event first, Event second);

    /**
     * Marks a relationship as important to the consistency property.
     * @param first
     * Event of the verified program.
     * @param second
     * Another event of the verified program.
     * @return
     * The knowledge increased by this operation.
     */
    boolean activate(Event first, Event second);

    /**
     * This implementation stores a direct mapping from {@code first} events to a mapping from {@code second} events to
     */
    final class Unidirectional implements Knowledge {

        @Override
        public Value get(Event first, Event second) {
            return map.getOrDefault(first,Map.of()).get(second);
        }

        @Override
        public Collection<Event> allFirst() {
            return map.keySet();
        }

        @Override
        public Collection<Event> byFirst(Event first) {
            return map.getOrDefault(first,Map.of()).keySet();
        }

        @Override
        public Collection<Event> allSecond() {
            return map.values().stream()
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(toList());
        }

        @Override
        public Collection<Event> bySecond(Event second) {
            return map.entrySet().stream()
                .filter(e->e.getValue().containsKey(second))
                .map(Map.Entry::getKey)
                .collect(toList());
        }

        @Override
        public boolean add(Event first, Event second) {
            Map<Event,Value> m = map.computeIfAbsent(first,k->new HashMap<>());
            return m.putIfAbsent(second,Value.NONE) == null;
        }

        @Override
        public boolean enable(Event first, Event second) {
            Map<Event,Value> m = map.computeIfAbsent(first,k->new HashMap<>());
            Value old = m.get(second);
            if(old == Value.TRUE || old == Value.BOTH) {
                return false;
            }
            m.put(second, old == Value.FALSE ? Value.BOTH : Value.TRUE);
            return true;
        }

        @Override
        public boolean disable(Event first, Event second) {
            Map<Event,Value> m = map.computeIfAbsent(first,k->new HashMap<>());
            Value old = m.get(second);
            if(old == Value.FALSE || old == Value.BOTH) {
                return false;
            }
            m.put(second, old == Value.TRUE ? Value.BOTH : Value.FALSE);
            return true;
        }

        @Override
        public boolean activate(Event first, Event second) {
            Map<Event,Value> m = map.computeIfAbsent(first,k->new HashMap<>());
            Value old = m.get(second);
            if(old == null || old == Value.NONE) {
                m.put(second,Value.CARE);
                return true;
            }
            return false;
        }

        private final Map<Event,Map<Event,Value>> map = new HashMap<>();
    }
}
