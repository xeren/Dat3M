package com.dat3m.dartagnan.wmm.analysis.relationAnalysis;

import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 * Knowledge contains static must-information about a relation
 * by assigning unknown (?), true (T), false (F) and contradiction (TF) to each tuple of the relation.
 * To represent these 4 cases, we use two sets:
 * <br> - The maySet is the complement of the must-false knowledge
 * <br> - and the mustSet is the must-true knowledge.
 *
 * <br> We then have that a tuple is
 * <br> - ?, if it is in may but not in must,
 * <br> - F, if it is not in may,
 * <br> - T, if it is in must and may,
 * <br> - TF, if it is in must but not may.
 * <br> The lattice ordering is ? <= F, T <= TF.
 *
 *
 * We also use Knowledge with the standard subset ordering (element-wise).
 * For that ordering, we use "SetDelta" to represent changes, while for
 * the typical knowledge, we use "Delta".
 */
public class Knowledge {
    private final TupleSet maySet;
    private final TupleSet mustSet;

    public TupleSet getMaySet() { return maySet; }
    public TupleSet getMustSet() { return mustSet; }

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

    public SetDelta asSetDelta() {
        return new SetDelta(maySet, mustSet);
    }




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
