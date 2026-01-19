package com.dat3m.dartagnan.program.filter;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.core.*;

import static com.dat3m.dartagnan.program.event.Tag.*;

public class GenMCTagFilter extends TagFilter {

    private final Class<?> firstClass;
    private final Class<?> secondClass;

    public GenMCTagFilter(final String tag) {
        super(tag);
        firstClass = switch (tag) {
            case VISIBLE, MEMORY -> MemoryCoreEvent.class;
            case INIT -> Init.class;
            case READ -> Load.class;
            case WRITE -> Store.class;
            case FENCE -> GenericVisibleEvent.class;
            default -> null;
        };
        secondClass = VISIBLE.equals(tag) ? GenericVisibleEvent.class : null;
    }

    public GenMCTagFilter(final TagFilter tagFilter) {
        this(tagFilter.tag);
    }

    @Override
    public boolean apply(final Event event) {
        return firstClass.isInstance(event) || (secondClass != null && secondClass.isInstance(event));
    }

    public boolean isEffective() {
        return firstClass != null;
    }
}
