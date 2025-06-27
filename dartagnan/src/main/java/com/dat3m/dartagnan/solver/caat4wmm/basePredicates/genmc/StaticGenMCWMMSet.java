package com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc;

import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.predicates.CAATPredicate;
import com.dat3m.dartagnan.solver.caat.predicates.Derivable;
import com.dat3m.dartagnan.solver.caat.predicates.misc.PredicateVisitor;
import com.dat3m.dartagnan.solver.caat.predicates.sets.Element;
import com.dat3m.dartagnan.solver.caat.predicates.sets.SetPredicate;
import com.dat3m.dartagnan.verification.model.EventData;

import java.util.*;
import java.util.stream.Stream;

public class StaticGenMCWMMSet extends AbstractGenMCWMMPredicate implements SetPredicate {

    private final Filter filter;
    private List<Element> events;

    public StaticGenMCWMMSet(Filter filter) {
        this.filter = filter;
    }

    @Override
    public <TRet, TData, TContext> TRet accept(PredicateVisitor<TRet, TData, TContext> visitor, TData tData, TContext context) {
        return visitor.visitBaseSet(this, tData, context);
    }

    @Override
    public void repopulate() {
        if (isRepopulateForbidden()) return;
        events = new ArrayList<>();
        for (EventData event : trace.getElements()) {
            if (filter.apply(event.getEvent())) {
                events.add(new Element(event.getId()));
            }
        }
    }

    @Override
    public void backtrackTo(int time) {
    }

    @Override
    public Collection<Element> forwardPropagate(CAATPredicate changedSource, Collection<? extends Derivable> added) {
        return Collections.emptyList();
    }

    @Override
    public List<SetPredicate> getDependencies() {
        return Collections.emptyList();
    }

    @Override
    public int size() {
        return events.size();
    }

    @Override
    public boolean containsById(int id) {
        return filter.apply(getEvent(id).getEvent());
    }

    @Override
    public Element get(Element e) {
        return getById(e.getId());
    }

    @Override
    public Element getById(int id) {
        return containsById(id) ? new Element(id) : null;
    }

    @Override
    public Stream<Element> elementStream() {
        return events.stream();
    }

    @Override
    public Iterator<Element> elementIterator() {
        return events.iterator();
    }

    @Override
    public Iterable<Element> elements() {
        return events;
    }
}
