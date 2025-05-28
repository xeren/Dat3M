package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.IRHelper;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.core.annotations.FunCallMarker;
import com.dat3m.dartagnan.program.event.core.annotations.FunReturnMarker;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Stack;

public class Simplifier implements FunctionProcessor {

    private Simplifier() { }

    public static Simplifier newInstance() {
        return new Simplifier();
    }

    public static Simplifier fromConfig(Configuration config) throws InvalidConfigurationException {
        return newInstance();
    }

    @Override
    public void run(Function function) {
        if (function.hasBody()) {
            simplify(function);
        }
    }

    private void simplify(Function func) {
        final var replacement = new LinkedHashMap<Event, List<Event>>();
        final var dependencies = new HashMap<Event, List<Event>>();
        final var stack = new Stack<Integer>();
        Event top = null;
        final List<Event> events = func.getEvents();
        for (int i = 0; i < events.size(); i++) {
            final Event event = events.get(i);
            if (simplifiable(event)) {
                replacement.put(event, List.of());
            } else if (simplifiablePair(event, null)) {
                stack.push(i);
                top = event;
            } else if (simplifiablePair(top, event)) {
                replacement.put(top, List.of());
                replacement.put(event, List.of());
                dependencies.put(top, events.subList(stack.peek() + 1, i));
                dependencies.put(event, List.of(top));
                stack.pop();
                top = stack.empty() ? null : events.get(stack.peek());
            } else {
                stack.clear();
                top = null;
            }
        }
        IRHelper.replace(replacement, dependencies, false);
    }

    private boolean simplifiable(Event event) {
        return event instanceof Label label && label.getJumpSet().isEmpty() && label != label.getFunction().getExit();
    }

    private boolean simplifiablePair(Event event, Event successor) {
        if (event instanceof CondJump jump) {
            return jump.getGuard().getRegs().isEmpty() && (successor == null || successor == jump.getLabel());
        }
        if (event instanceof FunCallMarker fc) {
            return successor == null ||
                    successor instanceof FunReturnMarker fr && fc.getFunctionName().equals(fr.getFunctionName());
        }
        return false;
    }

}