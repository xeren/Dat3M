package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.expression.booleans.BoolLiteral;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.IRHelper;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.functions.AbortIf;
import com.dat3m.dartagnan.program.event.functions.Return;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;

import java.util.HashSet;
import java.util.Set;

public class UnreachableCodeElimination implements FunctionProcessor {

    private UnreachableCodeElimination() { }

    public static UnreachableCodeElimination newInstance() {
        return new UnreachableCodeElimination();
    }

    public static UnreachableCodeElimination fromConfig(Configuration config) throws InvalidConfigurationException {
        return newInstance();
    }

    @Override
    public void run(Function function) {
        if (!function.hasBody()) {
            return;
        }

        final Event entry = function.getEntry();
        // When running on threads, we avoid deleting the exit event.
        final Event exit = function instanceof Thread ? function.getExit() : null;

        final Set<Event> unreachableEvents = new HashSet<>(function.getEvents());
        collectUnreachableEvents(entry, unreachableEvents);
        unreachableEvents.removeIf(e -> e.hasTag(Tag.NOOPT));
        unreachableEvents.remove(exit);
        IRHelper.bulkDelete(unreachableEvents);
    }

    // Modifies the second parameter
    private void collectUnreachableEvents(Event start, Set<Event> unreachable) {
        Event e = start;
        while (e != null && unreachable.remove(e)) {
            if (isTerminator(e)) {
                break;
            }
            if (e instanceof CondJump jump) {
                final Label jumpTarget = jump.getLabel();
                if (jump.isGoto()) {
                    e = jumpTarget;
                } else {
                    collectUnreachableEvents(jumpTarget, unreachable);
                    e = e.getSuccessor();
                }
            } else {
                e = e.getSuccessor();
            }
        }
    }

    private boolean isTerminator(Event e) {
        return e instanceof Return
                || e instanceof AbortIf abort && abort.getCondition() instanceof BoolLiteral b && b.getValue();
    }

}