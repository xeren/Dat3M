package com.dat3m.dartagnan.miscellaneous;

import com.dat3m.dartagnan.configuration.Property;
import com.dat3m.dartagnan.expression.BNonDet;
import com.dat3m.dartagnan.expression.IValue;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.analysis.BranchEquivalence;
import com.dat3m.dartagnan.program.analysis.Dependency;
import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.analysis.ThreadSymmetry;
import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.program.memory.Memory;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.program.processing.ProcessingManager;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.analysis.WmmAnalysis;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.NewRelationAnalysis;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.RelationAnalysisResult;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example.*;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.NewWmm;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.junit.Test;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.SolverContext;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.dat3m.dartagnan.configuration.OptionNames.PHANTOM_REFERENCES;
import static com.dat3m.dartagnan.program.event.EventFactory.*;

public class KnowledgeTest {

    // Test task where initial knowledge is already closed.
    @Test
    public void composition() throws InvalidConfigurationException {

        Memory memory = new Memory();
        MemoryObject x = memory.allocate(1);
        MemoryObject y = memory.allocate(1);

        Program p = new Program("composition", memory, Program.SourceLanguage.LITMUS);
        p.add(new Thread(10,newInit(x,0)));
        p.add(new Thread(11,newInit(y,0)));

        // r=x; if(*) x=1; else x=2; s=y; t=x; y=1;
        {
            Thread t0 = new Thread(0, newSkip());
            t0.append(newLoad(t0.newRegister("r",-1),x,null));
            Label label = newLabel("else");
            t0.append(newJump(new BNonDet(1), label));
            t0.append(newStore(x,new IValue(BigInteger.ONE,-1),null));
            Label join = newLabel("join");
            t0.append(newGoto(join));
            t0.append(label);
            t0.append(newStore(x,new IValue(BigInteger.TWO,-1),null));
            t0.append(join);
            t0.append(newLoad(t0.newRegister("s",-1),y,null));
            t0.append(newLoad(t0.newRegister("t",-1),x,null));
            t0.append(newStore(y,new IValue(BigInteger.ONE,-1),null));
            p.add(t0);
        }

        // r=y; s=x;
        {
            Thread t1 = new Thread(1,newSkip());
            t1.append(newStore(y,new IValue(BigInteger.TWO,-1),null));
            t1.append(newLoad(t1.newRegister("r",-1),y,null));
            t1.append(newLoad(t1.newRegister("s",-1),x,null));
            p.add(t1);
        }

        //Sequential Consistency
        NewWmm m = NewWmm.createAnarchicWmm();
        Relation rf = m.getRelationByNameOrTerm("rf");
        Relation co = m.getRelationByNameOrTerm("co");
        Relation po = m.newRelationWithName("po");
        Relation rfinv = m.newRelation();
        m.addDefinition(new Inverse(rfinv,rf));
        Relation fr = m.newRelationWithName("fr");
        m.addDefinition(new Composition(fr,rfinv,co));
        Relation hb = m.newRelationWithName("hb");
        m.addDefinition(new Union(hb,po,rf,co,fr));
        m.addAxiom(new Acyclic(hb));

        VerificationTask task = VerificationTask.builder().build(p, new Wmm(), EnumSet.noneOf(Property.class));
        Configuration config = task.getConfig();
        Context analysisContext = Context.create();
        Program program = task.getProgram();
        ProcessingManager.fromConfig(config).run(program);
        analysisContext.register(BranchEquivalence.class, BranchEquivalence.fromConfig(program, config));
        analysisContext.register(ExecutionAnalysis.class, ExecutionAnalysis.fromConfig(program, analysisContext, config));
        analysisContext.register(Dependency.class, Dependency.fromConfig(program, analysisContext, config));
        analysisContext.register(AliasAnalysis.class, AliasAnalysis.fromConfig(program, config));
        analysisContext.register(ThreadSymmetry.class, ThreadSymmetry.fromConfig(program, config));
        for(Thread thread : program.getThreads()) {
            for(Event e : thread.getEvents()) {
                // Some events perform static analyses by themselves (e.g. Svcomp's EndAtomic)
                // which may rely on previous "global" analyses
                e.runLocalAnalysis(program, analysisContext);
            }
        }
        analysisContext.register(WmmAnalysis.class, WmmAnalysis.fromConfig(task.getMemoryModel(), config));
        analysisContext.register(RelationAnalysis.class, RelationAnalysis.fromConfig(task, analysisContext, config));

        // ---- Assume po ---- (We have not definition for po yet)
        TupleSet enabled = new TupleSet();
        TupleSet disabled = new TupleSet();
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        for(Thread t : p.getThreads()) {
            List<Event> events = t.getCache().getEvents(FilterBasic.get(Tag.VISIBLE));
            for(int i = 0; i < events.size(); i++) {
                Event e0 = events.get(i);
                disabled.add(new Tuple(e0,e0));
                for(Event e1 : events.subList(0,i)) {
                    disabled.add(new Tuple(e0,e1));
                    (exec.areMutuallyExclusive(e0,e1) ? disabled : enabled).add(new Tuple(e1,e0));
                }
                p.getThreads().stream()
                .filter(u->u!=t)
                .flatMap(u->u.getCache().getEvents(FilterBasic.get(Tag.VISIBLE)).stream())
                .forEach(e1->{
                    disabled.add(new Tuple(e0,e1));
                    disabled.add(new Tuple(e1,e0));
                });
            }
        }
        m.addAxiom(new Assumption(po, disabled, enabled));

        NewRelationAnalysis analysis = new NewRelationAnalysis(m);
        Map<Relation, Knowledge> know = analysis.computeKnowledge(task, analysisContext);
        Map<Relation, TupleSet> activeSet = analysis.computeActiveSets(know);

        ShutdownManager sdm = ShutdownManager.create();

        Configuration solverConfig = Configuration.builder()
        .setOption(PHANTOM_REFERENCES,"true")
        .build();
        try(SolverContext ctx = SolverContextFactory.createSolverContext(
                solverConfig,
                BasicLogManager.create(solverConfig),
                sdm.getNotifier(),
                SolverContextFactory.Solvers.Z3)) {

            java.lang.Thread t = new java.lang.Thread(() -> {
                try {
                    // Converts timeout from secs to millisecs
                    java.lang.Thread.sleep(1000L);
                    sdm.requestShutdown("Shutdown Request");
                }
                catch(InterruptedException e) {
                    // Verification ended, nothing to be done.
                }});
            t.start();
            EncodingContext encCtx = new EncodingContext(ctx, analysisContext);
            BooleanFormula formula = m.encode(new RelationAnalysisResult(know, activeSet), encCtx);
            t.interrupt();
        }
    }
}
