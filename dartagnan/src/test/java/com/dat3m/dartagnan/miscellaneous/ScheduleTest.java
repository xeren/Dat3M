package com.dat3m.dartagnan.miscellaneous;

import com.dat3m.dartagnan.GlobalSettings;
import com.dat3m.dartagnan.configuration.Arch;
import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.ProgramEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.encoding.SymmetryEncoder;
import com.dat3m.dartagnan.encoding.WmmEncoder;
import com.dat3m.dartagnan.expression.BConst;
import com.dat3m.dartagnan.expression.IValue;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.EventFactory;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.SchedulePoint;
import com.dat3m.dartagnan.utils.rules.CSVLogger;
import com.dat3m.dartagnan.utils.rules.Provider;
import com.dat3m.dartagnan.utils.rules.Providers;
import com.dat3m.dartagnan.utils.rules.RequestShutdownOnError;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.definition.Schedule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.stream.IntStream;

import static com.dat3m.dartagnan.utils.ResourceHelper.TEST_RESOURCE_PATH;
import static com.dat3m.dartagnan.verification.solving.ModelChecker.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public final class ScheduleTest {

    @Test(timeout=180000L)
    public void testSafeStackBaseline() throws Exception {
        test();
    }

    @Test(timeout=600000L)
    public void testSafeStackInjected() throws Exception {
        // fix T2:E172 -hb> T3:E358 -hb> T3:E283 -hb> T4:E692
        test(172, 358, 383, 692);
    }

    private void test(int... ids) throws Exception {
        VerificationTask task = taskProvider.get();
        assertTrue(task.getMemoryModel().getRelations().stream().anyMatch(r -> r.getDefinition() instanceof Schedule));
        Context analysisContext = Context.create();
        preprocessProgram(task, task.getConfig());
        preprocessMemoryModel(task);
        Event[] events = IntStream.of(ids)
                .mapToObj(i -> task.getProgram().getEvents().stream().filter(e -> e.getCId() == i).findFirst().orElseThrow())
                .toArray(Event[]::new);
        SchedulePoint[] schedule = IntStream.range(0, 2 * (ids.length - 1))
                .mapToObj(i -> EventFactory.Svcomp.newSchedulePoint(BConst.TRUE, new IValue(BigInteger.valueOf(i), 0)))
                .toArray(SchedulePoint[]::new);
        int cId = task.getProgram().getEvents().stream().mapToInt(Event::getCId).max().orElseThrow();
        for (int i = 0; i < ids.length - 1; i++) {
            Event p = events[i];
            Event r = p.getSuccessor();
            SchedulePoint q = schedule[2 * i];
            q.setThread(p.getThread());
            q.setCId(++cId);
            q.setPredecessor(p);
            q.setSuccessor(r);
            p.setSuccessor(q);
            r.setPredecessor(q);
            SchedulePoint t = schedule[2 * i + 1];
            Event u = events[i + 1];
            Event s = u.getPredecessor();
            t.setThread(u.getThread());
            t.setCId(++cId);
            t.setPredecessor(s);
            t.setSuccessor(u);
            s.setSuccessor(t);
            u.setPredecessor(t);
        }
        task.getProgram().clearCache(true);
        // Static Analyses
        performStaticProgramAnalyses(task, analysisContext, task.getConfig());
        performStaticWmmAnalyses(task, analysisContext, task.getConfig());
        // Encode the problem
        SolverContext ctx = contextProvider.get();
        EncodingContext context = EncodingContext.of(task, analysisContext, ctx.getFormulaManager());
        ProgramEncoder programEncoder = ProgramEncoder.withContext(context);
        PropertyEncoder propertyEncoder = PropertyEncoder.withContext(context);
        WmmEncoder wmmEncoder = WmmEncoder.withContext(context);
        SymmetryEncoder symmetryEncoder = SymmetryEncoder.withContext(context, task.getMemoryModel(), analysisContext);
        programEncoder.initializeEncoding(ctx);
        propertyEncoder.initializeEncoding(ctx);
        wmmEncoder.initializeEncoding(ctx);
        symmetryEncoder.initializeEncoding(ctx);
        // Prove the problem
        ProverEnvironment prover = proverProvider.get();
        prover.addConstraint(propertyEncoder.encodeSpecification());
        BooleanFormula fullProgram = programEncoder.encodeFullProgram();
        prover.addConstraint(fullProgram);
        BooleanFormula fullMemoryModel = wmmEncoder.encodeFullMemoryModel();
        prover.addConstraint(fullMemoryModel);
        prover.addConstraint(task.getWitness().encode(context));
        prover.addConstraint(symmetryEncoder.encodeFullSymmetryBreaking());
        assertFalse(prover.isUnsat());
    }

    @Parameterized.Parameters(name = "{index}")
    public static Iterable<Object[]> data() throws IOException {
        ArrayList<Object[]> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            list.add(new Object[0]);
        }
        return list;
    }

    @ClassRule
    public static CSVLogger.Initialization csvInit = CSVLogger.Initialization.create();

    // Provider rules
    private final Provider<ShutdownManager> shutdownManagerProvider = Provider.fromSupplier(ShutdownManager::create);
    private final Provider<Arch> targetProvider = () -> Arch.C11;
    private final Provider<String> filePathProvider = Provider.fromSupplier(() -> TEST_RESOURCE_PATH + "lfds/safe_stack-3.bpl");
    private final Provider<Integer> boundProvider = Provider.fromSupplier(() -> 2);
    private final Provider<Program> programProvider = Providers.createProgramFromPath(filePathProvider);
    private final Provider<Wmm> wmmProvider = GlobalSettings.ATOMIC_AS_LOCK ?
            Providers.createWmmFromName(() -> "svcomp-locks") :
            Providers.createWmmFromName(() -> "svcomp");
    private final Provider<EnumSet<Property>> propertyProvider = Provider.fromSupplier(() -> EnumSet.of(Property.REACHABILITY));
    private final Provider<VerificationTask> taskProvider = Providers.createTask(programProvider, wmmProvider, propertyProvider, targetProvider, boundProvider, Configuration::defaultConfiguration);
    private final Provider<SolverContext> contextProvider = Providers.createSolverContextFromManager(shutdownManagerProvider);
    private final Provider<ProverEnvironment> proverProvider = Providers.createProverWithFixedOptions(contextProvider, SolverContext.ProverOptions.GENERATE_MODELS);

    // Special rules
    private final RequestShutdownOnError shutdownOnError = RequestShutdownOnError.create(shutdownManagerProvider);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(shutdownManagerProvider)
            .around(shutdownOnError)
            .around(filePathProvider)
            .around(boundProvider)
            .around(programProvider)
            .around(wmmProvider)
            .around(propertyProvider)
            .around(taskProvider)
            // Context/Prover need to be created inside test-thread spawned by <timeout>
            .around(contextProvider)
            .around(proverProvider);
}
