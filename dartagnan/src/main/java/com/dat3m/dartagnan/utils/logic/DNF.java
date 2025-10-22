package com.dat3m.dartagnan.utils.logic;

import com.google.common.collect.Iterables;

import java.util.*;
import java.util.function.Predicate;

// A formal minimal(*) disjunction of conjunctions of type T
// The class is immutable
// The ordering is based on set inclusion. FALSE is the least element and TRUE is the largest element
// (*) the minimality is weak in the sense that only duplicates are avoided.
public class DNF<T extends Literal<T>> implements PartialOrder<DNF<T>> {

    private static final DNF FALSE;
    private static final DNF TRUE;

    static {
        FALSE = new DNF(Collections.EMPTY_SET);
        TRUE = new DNF(Conjunction.TRUE());
    }


    public static <V extends Literal<V>> DNF<V> TRUE() { return TRUE; }
    public static <V extends Literal<V>> DNF<V> FALSE() { return FALSE; }

    // Using a sorted list is better (e.g. sorted by size)
    private final List<Conjunction<T>> reasons;
    private int hashCode;

    // This set should never be modified
    public List<Conjunction<T>> getCubes() {
        return Collections.unmodifiableList(reasons);
    }

    public int getNumberOfCubes() {
        return reasons.size();
    }

    public int getTotalSize() {
        return reasons.stream().mapToInt(Conjunction::getSize).sum();
    }

    public boolean isFalse() {
        return reasons.isEmpty();
    }

    // Note: This class does not reduce disjunctions of the form "p or not p" to true.
    public boolean isTriviallyTrue() {
        return this.equals(TRUE);
    }

    public static <V extends Literal<V>> DNF<V> byLiteral(final boolean literal) {
        return literal ? TRUE() : FALSE();
    }

    public DNF(final T literal) {
        this(new Conjunction<>(literal));
    }

    public DNF(final Conjunction<T> cube) {
        if (cube.isFalse() && FALSE != null) {
            this.reasons = FALSE.reasons;
            this.hashCode = FALSE.hashCode;
        } else if (cube.isTrue() && TRUE != null) {
            this.reasons = TRUE.reasons;
            this.hashCode = TRUE.hashCode;
        } else {
            reasons = Collections.singletonList(cube);
            computeHash();
        }
    }

    public DNF(final Collection<Conjunction<T>> reasons) {
        this(new ArrayList<>(reasons), true);
    }

    public DNF(final Conjunction<T>... reasons) {
        this(Arrays.asList(reasons));
    }

    protected DNF(final List<Conjunction<T>> reasons) {
        this(reasons, true);
    }

    protected DNF(final List<Conjunction<T>> reasons, final boolean reduce) {
        this.reasons = reasons;
        reasons.sort(Comparator.comparing(Conjunction::getSize));
        if (reduce) {
            reduce();
        }
        computeHash();
    }

    // Keeps the DNF minimal (without self-subsumption or any other advanced techniques)
    private void reduce() {
        for (final Iterator<Conjunction<T>> it = reasons.iterator(); it.hasNext(); ) {
            final Conjunction<T> cube = it.next();
            if (cube.isFalse()) {
                it.remove();
                continue;
            } // We could also shortcut when finding a true cube

            boolean isDominated = false;
            for (final Conjunction<T> cube2 : reasons) {
                if (cube.compareToPartial(cube2) == OrderResult.GT) {
                    isDominated = true;
                    break;
                }
            }
            if (isDominated) {
                it.remove();
            }
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private void computeHash() {
        hashCode = 1;
        for (final Conjunction<T> cube : reasons) {
            int cubeHash = cube.hashCode();
            assert (!cube.isFalse());
            hashCode *= cubeHash; // Use * instead of + (important!)
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || obj.getClass() != DNF.class) {
            return false;
        }

        final DNF<T> other = (DNF<T>) obj;

        if (this.hashCode != other.hashCode
                || this.getNumberOfCubes() != other.getNumberOfCubes()
                || this.getTotalSize() != other.getTotalSize()) {
            return false;
        }

        return this.reasons.containsAll(other.reasons);
    }

    @Override
    public OrderResult compareToPartial(final DNF<T> other) {
        if (equals(other)) {
            return OrderResult.EQ;
        } else if (this.isFalse() || other.isTriviallyTrue()) {
            return OrderResult.LT;
        } else if (other.isFalse() || this.isTriviallyTrue()) {
            return OrderResult.GT;
        }

        // We are returning here with the assumption that equality is not possible
        if (isLessThan(other)) {
            return OrderResult.LT;
        }
        if (other.isLessThan(this)) {
            return OrderResult.GT;
        }

        return OrderResult.INCOMP;
    }

    public boolean isLessThan(final DNF<T> other) {
        for (final Conjunction<T> cube1 : this.reasons) {
            if (other.reasons.contains(cube1)) {
                continue;
            }
            boolean found = false;
            for (final Conjunction<T> cube2 : other.reasons) {
                final OrderResult cmp = cube1.compareToPartial(cube2);
                if (cmp == OrderResult.LT || cmp == OrderResult.EQ) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "{ " + String.join(" | ", Iterables.transform(reasons, Conjunction::toString)) + " }";
    }

    public DNF<T> remove(final Collection<Conjunction<T>> reasons) {
        if (this.isTriviallyTrue() || this.isFalse()
                || this.reasons.stream().noneMatch(reasons::contains)) {
            return this;
        }

        final List<Conjunction<T>> result = new ArrayList<>(this.reasons);
        result.removeAll(reasons);
        return new DNF<>(result, false);

    }


    public DNF<T> or(final DNF<T> other) {
        if (this.isTriviallyTrue() || other.isTriviallyTrue()) {
            return TRUE();
        } else if (this.isFalse()) {
            return other.isFalse() ? FALSE() : other;
        } else if (other.isFalse()) {
            return this;
        }

        final List<Conjunction<T>> result = new ArrayList<>(this.reasons);
        result.addAll(other.reasons);
        return new DNF<>(result);
    }

    public DNF<T> and(final DNF<T> other) {
        return and(other, true);
    }

    public DNF<T> and(final DNF<T> other, final boolean disjoint) {
        if (this.isFalse() || other.isFalse()) {
            return FALSE();
        } else if (this.isTriviallyTrue()) {
            return other;
        } else if (other.isTriviallyTrue()) {
            return this;
        }

        final HashSet<Conjunction<T>> result = new HashSet<>(this.getNumberOfCubes() * other.getNumberOfCubes());
        for (final Conjunction<T> cube1 : this.reasons) {
            for (final Conjunction<T> cube2 : other.reasons) {
                result.add(cube1.and(cube2, disjoint));
            }
        }
        return new DNF<>(result);
    }

    public boolean evaluate(final Predicate<T> literalEvaluator) {
        for (final Conjunction<T> reason : reasons) {
            if (reason.evaluate(literalEvaluator)) {
                return true;
            }
        }
        return false;
    }
}
