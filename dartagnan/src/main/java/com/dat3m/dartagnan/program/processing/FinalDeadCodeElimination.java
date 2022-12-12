package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.dat3m.dartagnan.configuration.OptionNames.ENABLE_FINAL_DEAD_CODE_ELIMINATION;
import static com.google.common.base.Preconditions.checkState;

/**
 * Removes dead jumps, as well as unreachable branches,
 * which were produced by previous program processors.
 * <p>
 * This procedure should never remove live control dependencies,
 * since registers modified by a load event are non-constant.
 */
@Options
public final class FinalDeadCodeElimination implements ProgramProcessor {

    private static final Logger logger = LogManager.getLogger(FinalDeadCodeElimination.class);

    @Option(name = ENABLE_FINAL_DEAD_CODE_ELIMINATION,
            description = "Removes dead code introduced during preprocessing, unrolling and compiling." +
                    "\nDefault: true.",
            secure = true)
    private boolean enable = true;

    private FinalDeadCodeElimination() {
    }

    public static FinalDeadCodeElimination fromConfig(Configuration config) throws InvalidConfigurationException {
        FinalDeadCodeElimination e = new FinalDeadCodeElimination();
        config.inject(e);
        return e;
    }

    @Override
    public void run(Program program) {
        if (!enable) {
            return;
        }
        for (Thread thread : program.getThreads()) {
            run(thread);
        }
        program.clearCache(true);
    }

    private void run(Thread thread) {
        List<Event> removable = new ArrayList<>();
        Set<Event> reachableLabels = new HashSet<>();
        for (Event e = thread.getEntry(); e != null;) {
            reachableLabels.remove(e);
            if (!(e instanceof CondJump)) {
                e = e.getSuccessor();
                continue;
            }
            CondJump j = (CondJump) e;
            if (j.isDead()) {
                removable.add(j);
            } else {
                reachableLabels.add(j.getLabel());
            }
            e = e.getSuccessor();
            if (j.isGoto()) {
                while (e != null && !reachableLabels.remove(e)) {
                    removable.add(e);
                    e = e.getSuccessor();
                }
            }
            if (j.getLabel().equals(e)) {
                removable.add(j);
            }
        }
        checkState(reachableLabels.isEmpty(),
                "Dangling or looping labels in thread %s \"%s\": %s",
                thread.getId(), thread.getName(), reachableLabels);
        if (removable.isEmpty()) {
            return;
        }
        logger.debug("Found {} unreachable events in thread {} \"{}\"", removable.size(), thread.getId(), thread.getName());
        for (Event e : removable) {
            if (!e.is(Tag.NOOPT) || e.is(Tag.BOUND)) {
                e.delete();
            }
        }
    }

}
