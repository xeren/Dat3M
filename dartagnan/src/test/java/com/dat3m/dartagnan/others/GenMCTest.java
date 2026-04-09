package com.dat3m.dartagnan.others;

import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.type.TypeFactory;
import com.dat3m.dartagnan.program.Entrypoint;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.memory.Memory;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.verification.solving.GenMCSolver;
import com.dat3m.dartagnan.wmm.Wmm;
import org.junit.Test;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.EnumSet;
import java.util.List;

import static com.dat3m.dartagnan.configuration.OptionNames.ASSERTION_GUIDANCE;
import static com.dat3m.dartagnan.program.event.EventFactory.*;
import static org.junit.Assert.assertTrue;

public class GenMCTest {

    private static final TypeFactory types = TypeFactory.getInstance();
    private static final ExpressionFactory expressions = ExpressionFactory.getInstance();

    /// Assertion Guidance should have no benefit for this program,
    /// because all loads are (naively) relevant to the assertion.
    @Test
    public void assertionGuidance() throws Exception {
        final var i32 = types.getIntegerType(32);
        final var threadType = types.getFunctionType(types.getVoidType(), List.of());

        final var program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        program.setEntrypoint(new Entrypoint.Resolved());
        final MemoryObject o1 = program.getMemory().allocate(4);
        final MemoryObject o2 = program.getMemory().allocate(4);
        o1.setInitialValue(0, expressions.makeZero(i32));
        o2.setInitialValue(0, expressions.makeZero(i32));

        final var t0 = new Thread("t0", threadType, List.of(), 0, new ThreadStart(null));
        final Register r1 = t0.newRegister("r1", i32);
        final Register r2 = t0.newRegister("r2", i32);
        final Label l1 = newLabel("l1");
        final Label l2 = newLabel("l2");
        t0.append(List.of(
                newLoad(r1, o1),
                newJump(expressions.makeLT(r1, expressions.makeZero(i32), true), l1),
                newLoad(r2, o2),
                newJump(expressions.makeLT(r2, expressions.makeZero(i32), true), l1),
                newGoto(l2),
                l1,
                newAssert(expressions.makeFalse(), "test"),
                l2
        ));
        program.addThread(t0);

        final var t1 = new Thread("t1", threadType, List.of(), 1, new ThreadStart(null));
        t1.append(newStore(o1, expressions.makeOne(i32)));
        program.addThread(t1);

        final var t2 = new Thread("t2", threadType, List.of(), 2, new ThreadStart(null));
        t2.append(newStore(o2, expressions.makeOne(i32)));
        program.addThread(t2);

        final var m = new Wmm();
        final var task = VerificationTask.builder()
                .withConfig(Configuration.builder().setOption(ASSERTION_GUIDANCE, "true").build())
                .build(program, m, EnumSet.of(Property.PROGRAM_SPEC));
        try (SolverContext solver = SolverContextFactory.createSolverContext(SolverContextFactory.Solvers.Z3);
             ProverEnvironment prover = solver.newProverEnvironment()) {
            GenMCSolver.run(solver, prover, task);
            //TODO extract collected reasons or found executions and compare
            // should be four executions and reasons
            // ( rf(I_o2,T0:0) && rf(I_o1,T0:1) )
            // ( rf(I_o2,T0:0) && rf(T2:0,T0:1) )
            // ( rf(T1:0,T0:0) && rf(I_o1,T0:1) )
            // ( rf(T1:0,T0:0) && rf(T2:0,T0:1) )
            // but are currently TRUE rsp. zero executions!
            assertTrue(false);
        }
    }
}
