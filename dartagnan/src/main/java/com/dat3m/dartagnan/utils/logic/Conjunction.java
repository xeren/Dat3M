package com.dat3m.dartagnan.utils.logic;

import com.google.common.collect.Iterables;

import java.util.*;
import java.util.function.Predicate;

// A formal minimal conjunction of ground literals of type T
// The class is immutable
// The ordering is based on set inclusion. TRUE is the least element, FALSE is the largest element
public class Conjunction<T extends Literal<T>> implements PartialOrder<Conjunction<T>>
{
    private static final Conjunction TRUE;
    private static final Conjunction FALSE;

    static {
        TRUE = new Conjunction(new HashSet());
        FALSE = new Conjunction(new HashSet());
        FALSE.unsat = true;
    }

    public static <V extends Literal<V>> Conjunction<V> TRUE() { return TRUE; }
    public static <V extends Literal<V>> Conjunction<V> FALSE() { return FALSE; }

    // TODO: Using an ordered list for the literals is probably better!
    protected final List<T> literals;
    protected Boolean unsat = false;
    protected int hashCode;

    // The returned Set should not be modified!
    public List<T> getLiterals() {
        return Collections.unmodifiableList(literals);
    }

    public boolean isTrue() {
        return !unsat && literals.isEmpty();
    }

    public boolean isFalse() {
        return unsat;
    }

    public int getSize() {
        return literals.size();
    }

    public Conjunction(final T literal) {
        literals = Collections.singletonList(literal);
        computeHash();
    }

    public Conjunction(final T ...literals) {
        this(Arrays.asList(literals));
    }

    public Conjunction(final Collection<T> literals) {
        this(new ArrayList<>(literals), true);
    }

    protected Conjunction(List<T> literals) {
        this(literals, true);
    }

    protected Conjunction(final List<T> literals, final boolean reduce) {
        this.literals = literals;
        if (reduce) {
            reduce();
        }
        computeHash();
    }

    protected void reduce() {
        if (literals.stream().anyMatch(lit -> literals.contains(lit.negated()))) {
            unsat = true;
            literals.clear();
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || !(obj.getClass() == this.getClass())) {
            return false;
        }

        final Conjunction<T> other = (Conjunction<T>)obj;
        if (this.hashCode != other.hashCode || this.literals.size() != other.literals.size()) {
            return false;
        }

        return literals.isEmpty() ? (unsat == other.unsat) : literals.containsAll(other.literals);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    // This is performed once on construction
    private void computeHash() {
        // We increase the hashCode by 1 to avoid a 0 hashcode for non-empty conjunctions
        hashCode = literals.hashCode() + (literals.isEmpty() ? 0 : 1);
        assert (isTrue() || isFalse() || hashCode != 0);
    }

    @Override
    public String toString() {
        return String.join(" & ", Iterables.transform(literals, Objects::toString));
    }

    public boolean isProperSubclauseOf(final Conjunction<T> other) {
        if (this.literals.size() >= other.literals.size()) {
            return false;
        }
        return other.literals.containsAll(this.literals);
    }

    @Override
    public OrderResult compareToPartial(final Conjunction<T> other) {
        if (equals(other)) {
            return OrderResult.EQ;
        } else if (this.isFalse()) {
            return OrderResult.GT;
        } else if (other.isFalse()) {
            return OrderResult.LT;
        } else if (this.getSize() == other.getSize()) {
            return OrderResult.INCOMP;
        } else if (this.getSize() < other.getSize()) {
            return other.getLiterals().containsAll(this.getLiterals()) ? OrderResult.LT : OrderResult.INCOMP;
        }

        return this.getLiterals().containsAll(other.getLiterals()) ? OrderResult.GT : OrderResult.INCOMP;
    }

    public Conjunction<T> and(final Conjunction<T> other) {
        return and(other, true);
    }

    public Conjunction<T> and(final Conjunction<T> other, final boolean disjoint) {
        if (this.isFalse() || other.isFalse()) {
            return FALSE();
        } else if (this.isTrue()) {
            return other.isTrue() ? TRUE() : other;
        } else if (other.isTrue()) {
            return this;
        } else if (this.isResolvableWith(other)) {
            return FALSE();
        }

        final OrderResult cmp = this.compareToPartial(other);
        if (cmp == OrderResult.EQ || cmp == OrderResult.GT) {
            return this;
        } else if (cmp == OrderResult.LT) {
            return other;
        }

        final ArrayList<T> result = new ArrayList<>(this.getSize() + other.getSize());
        result.addAll(this.getLiterals());
        final List<T> otherLiterals = other.getLiterals();
        if (disjoint) {
            result.addAll(otherLiterals);
        } else {
            for (final T literal: otherLiterals) {
                if (!literals.contains(literal)) {
                    result.add(literal);
                }
            }
        }

        return new Conjunction<>(result, false);
    }

    public Conjunction<T> removeIf(final Predicate<T> pred) {
        if (this.isFalse() || this.isTrue()) {
            return this;
        }
        final ArrayList<T> result = new ArrayList<>(this.getSize());
        literals.stream().filter(Predicate.not(pred)).forEach(result::add);
        return new Conjunction<>(result, false);
    }


    public boolean isResolvableWith(final Conjunction<T> other) {
        Conjunction<T> a = this;
        Conjunction<T> b = other;
        if (a.getSize() > b.getSize()) {
            a = other;
            b = this;
        }

        final List<T> bLits = b.getLiterals();
        return a.getLiterals().stream().anyMatch(lit -> bLits.contains(lit.negated()));
    }

    public List<T> toList() {
        return new ArrayList<>(literals);
    }

    public boolean evaluate(final Predicate<T> literalEvaluator) {
        for (final T literal : literals) {
            if (!literalEvaluator.test(literal)) {
                return false;
            }
        }
        return true;
    }
}
