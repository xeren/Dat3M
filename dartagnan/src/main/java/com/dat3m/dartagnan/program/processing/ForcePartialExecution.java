package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.expression.BConst;
import com.dat3m.dartagnan.expression.BExpr;
import com.dat3m.dartagnan.expression.BExprUn;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.ForceExecution;
import com.dat3m.dartagnan.program.event.core.Label;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static com.dat3m.dartagnan.configuration.OptionNames.ENABLE_FORCE_EXECUTION;
import static com.dat3m.dartagnan.expression.op.BOpUn.NOT;
import static com.dat3m.dartagnan.program.event.EventFactory.newAssume;

/**
 * Forces parts of the program to be executed.
 * Precedes conditional jumps with assumptions, if one option would bypass a branch that is marked mandatory.
 * Requires a sufficient ConstantPropagation.
 * @see ForceExecution
 */
@Options
public final class ForcePartialExecution implements ProgramProcessor {

    private static final Logger logger = LogManager.getLogger(ForcePartialExecution.class);

    @Option(name = ENABLE_FORCE_EXECUTION,
            description = "Marks branches that avoid some mandatory element of the program as dead code.  Default: true.",
            secure = true)
    private boolean enable = true;

    private ForcePartialExecution() {}

    public static ForcePartialExecution fromConfig(Configuration config) throws InvalidConfigurationException {
        ForcePartialExecution p = new ForcePartialExecution();
        config.inject(p);
        return p;
    }

    @Override
    public void run(Program program) {
        if (!enable) {
            return;
        }
        logger.trace("start");
        List<Event> list = new ArrayList<>();
        for (Event e : program.getEvents()) {
            if (e instanceof ForceExecution && ((ForceExecution) e).isEnabled()) {
                list.add(e);
            }
        }
        for (Event event : list) {
            run(event);
            event.getThread().clearCache();
        }
    }

    private void run(Event e) {
        List<CondJump> falseSet = new ArrayList<>();
        List<CondJump> trueSet = new ArrayList<>();
        Queue<Event> q = new ArrayDeque<>();
        while (e != null) {
            for (CondJump j : e instanceof Label ? ((Label) e).getJumpSet() : Set.<CondJump>of()) {
                trueSet.add(j);
                if (!falseSet.contains(j) && !j.isDead()) {
                    q.add(j);
                }
            }
            Event p = e.getPredecessor();
            boolean end;
            if (p instanceof CondJump) {
                falseSet.add((CondJump) p);
                end = ((CondJump) p).isGoto() || !q.remove(p);
            } else {
                end = p == null;
            }
            e = end ? q.poll() : p;
        }
        for (CondJump j : falseSet) {
            if (!trueSet.contains(j) && !j.isDead()) {
                // If j.isGoto(), j should be dead code when run returns
                assumeBefore(new BExprUn(NOT, j.getGuard()), j);
                j.setGuard(BConst.FALSE);
            }
        }
        for (CondJump j : trueSet) {
            if (!falseSet.contains(j) && !j.isGoto()) {
                assumeBefore(j.getGuard(), j);
                j.setGuard(BConst.TRUE);
            }
        }
        logger.debug("sets: {}, {}", falseSet.size(), trueSet.size());
    }

    private static void assumeBefore(BExpr condition, CondJump jump) {
        Event assumption = newAssume(condition);
        assumption.setCFileInformation(jump.getCLine(), jump.getSourceCodeFile());
        assumption.setThread(jump.getThread());
        assumption.setOId(jump.getOId());
        assumption.setUId(jump.getUId());
        Event predecessor = jump.getPredecessor();
        assumption.setPredecessor(predecessor);
        assumption.setSuccessor(jump);
        predecessor.setSuccessor(assumption);
        jump.setPredecessor(assumption);
    }
}
