package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.EventFactory;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Label;

import java.util.Set;

public class ConsecutiveGotoSimplification implements ProgramProcessor {

    private ConsecutiveGotoSimplification() { }

    public static ConsecutiveGotoSimplification newInstance() {
        return new ConsecutiveGotoSimplification();
    }

    @Override
    public void run(Program program) {
        simplifyConsecutiveGotos(program);
    }

    private void simplifyConsecutiveGotos(Program program) {
        for (Label label : program.getThreadEvents(Label.class)) {
            Set<CondJump> jumpSet = label.getJumpSet();
            if (jumpSet.size() == 1) {
                CondJump jump = jumpSet.iterator().next();
                if (label.getSuccessor() instanceof CondJump consecutiveJump && consecutiveJump.isGoto()
                        && label.getPredecessor() instanceof CondJump previousInstr && previousInstr.isGoto()) {
                    CondJump newJump = EventFactory.newJump(jump.getGuard(), consecutiveJump.getLabel());
                    jump.getPredecessor().setSuccessor(newJump);
                    jump.getSuccessor().setPredecessor(newJump);
                    newJump.setPredecessor(jump.getPredecessor());
                    newJump.setSuccessor(jump.getSuccessor());
                    newJump.setFunction(jump.getFunction());
                    label.forceDelete();
                    consecutiveJump.forceDelete();
                    simplifyConsecutiveGotos(program);
                    break;
                }
            }
        }
    }
}
