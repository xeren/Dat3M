package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis;

import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 * Knowledge contains static must-information about a relation
 * by assigning unknown (?), true (T), false (F) and contradiction (TF) to each tuple of the relation.
 * The lattice ordering is ? <= F, T <= TF.
 * To represent these 4 cases, we use two sets:
 * <ul>
 *     <li> maySet is the complement of the must-false knowledge </li>
 *     <li> mustSet is the must-true knowledge </li>
 * </ul>
 * We then have that a tuple is
 * <ul>
 *     <li> ?, if (may, not must) </li>
 *     <li> F, if (not may, not must) </li>
 *     <li> T, if (may, must) </li>
 *     <li> TF, if (not may, must) </li>
 * </ul>
 *
 *
 * <p>We also use Knowledge with the standard subset ordering (element-wise).
 * For that ordering, we use "SetDelta" to represent changes, while for
 * the typical knowledge, we use "Delta".
 * <br>
 * This class is mutable and its methods are mutating.
 */
public class Knowledge {
    private final TupleSet maySet;
    private final TupleSet mustSet;

    public TupleSet getMaySet() { return maySet; }
    public TupleSet getMustSet() { return mustSet; }

    // ======================== Construction ========================

    public Knowledge(TupleSet may, TupleSet must) {
        this.maySet = may;
        this.mustSet = must;
    }

    public Knowledge() {
        this(new TupleSet(), new TupleSet());
    }

    public static Knowledge newEmptySet() {
        return new Knowledge();
    }

    // ======================== Utility ========================


    @Override
    public String toString() {
        return String.format("(must: %s, may: %s)", mustSet.size(), maySet.size());
    }

    public boolean isFalse(Tuple t) {return !maySet.contains(t); }
    public boolean isTrue(Tuple t) { return mustSet.contains(t); }

    public boolean isUnknown(Tuple t) {
        return !isFalse(t) && !isTrue(t);
    }
    public boolean isStrictlyTrue(Tuple t) {
        return isTrue(t) && !isFalse(t);
    }
    public boolean isStrictlyFalse(Tuple t) {
        return isFalse(t) && !isTrue(t);
    }
    public boolean isContradicting(Tuple t) {
        return isFalse(t) && isTrue(t);
    }

    public SetDelta asSetDelta() {
        return new SetDelta(maySet, mustSet);
    }

    public SetDelta joinSet(SetDelta delta) {
        if (delta.isEmpty()) {
            return delta;
        }
        return new SetDelta(
                delta.addedMaySet.stream().filter(maySet::add).collect(TupleSet.collector()),
                delta.addedMustSet.stream().filter(mustSet::add).collect(TupleSet.collector())
        );
    }


    public Delta join(Delta delta) {
        if (delta.isEmpty()) {
            return delta;
        }
        return new Delta(
                delta.disabledSet.stream().filter(maySet::remove).collect(TupleSet.collector()),
                delta.enabledSet.stream().filter(mustSet::add).collect(TupleSet.collector())
        );
    }

    public Knowledge copy() {
        return new Knowledge(new TupleSet(maySet), new TupleSet(mustSet));
    }

    // =========================== Delta classes ===========================
    // NOTE: These classes are to be treated as immutable (although immutability cannot be enforced)

    // When treated as set
    public static class SetDelta {
        private final TupleSet addedMaySet;
        private final TupleSet addedMustSet;

        public TupleSet getAddedMaySet() { return addedMaySet; }
        public TupleSet getAddedMustSet() { return addedMustSet; }

        public SetDelta(TupleSet addedMay, TupleSet addedMust) {
            this.addedMaySet = addedMay;
            this.addedMustSet = addedMust;
        }

        public SetDelta() {
            this(new TupleSet(), new TupleSet());
        }

        public boolean isEmpty() {
            return addedMaySet.isEmpty() && addedMustSet.isEmpty();
        }
    }


    // When treated as knowledge
    public static class Delta {
        private final TupleSet disabledSet;
        private final TupleSet enabledSet;

        public TupleSet getDisabledSet() { return disabledSet; }
        public TupleSet getEnabledSet() { return enabledSet; }

        public Delta(TupleSet disabled, TupleSet enabled) {
            this.disabledSet = disabled;
            this.enabledSet = enabled;
        }

        public Delta() {
            this(new TupleSet(), new TupleSet());
        }

        public boolean isEmpty() {
            return disabledSet.isEmpty() && enabledSet.isEmpty();
        }
    }


}
