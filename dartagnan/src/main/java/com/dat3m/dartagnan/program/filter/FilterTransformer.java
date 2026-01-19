package com.dat3m.dartagnan.program.filter;

public abstract class FilterTransformer {

    public Filter visitUnion(final Filter filter1, final Filter filter2) {
        return new UnionFilter(filter1.accept(this), filter2.accept(this));
    }

    public Filter visitIntersection(final Filter filter1, final Filter filter2) {
        return new IntersectionFilter(filter1.accept(this), filter2.accept(this));
    }

    public Filter visitDifference(final Filter filter1, final Filter filter2) {
        return new DifferenceFilter(filter1.accept(this), filter2.accept(this));
    }

    public Filter visitTagFilter(final TagFilter filter) {
        return filter;
    }
}
