package com.dat3m.dartagnan.verification;

import com.dat3m.dartagnan.configuration.Baseline;
import com.dat3m.dartagnan.encoding.ProgramEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.encoding.SymmetryEncoder;
import com.dat3m.dartagnan.encoding.WmmEncoder;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.witness.WitnessGraph;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.analysis.WmmAnalysis;
import com.dat3m.dartagnan.wmm.axiom.Acyclic;
import com.dat3m.dartagnan.wmm.axiom.Empty;
import com.dat3m.dartagnan.wmm.Relation;
import org.apache.logging.log4j.LogManager;
import com.dat3m.dartagnan.wmm.utils.RelationRepository;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.EnumSet;

import static com.dat3m.dartagnan.configuration.Baseline.*;
import static com.dat3m.dartagnan.configuration.OptionNames.BASELINE;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.*;

/*
 A RefinementTask is a VerificationTask with an additional baseline memory model.
 The intention is that such a task is solved by any solving strategy that starts from the
 baseline memory model and refines it iteratively towards the target memory model.
 Currently, we only have a custom theory solver (CAAT) to solve such tasks but any CEGAR-like approach could be used.
 */
@Options
public final class RefinementTask extends VerificationTask {

	private static final Logger logger = LogManager.getLogger(RefinementTask.class);

    private final Wmm baselineModel;
    private Context baselineContext;
    private WmmEncoder baselineWmmEncoder;


    // =========================== Configurables ===========================

	@Option(name=BASELINE,
			description="Refinement starts from this baseline WMM.",
			secure=true,
			toUppercase=true)
		private EnumSet<Baseline> baselines = EnumSet.noneOf(Baseline.class);

    // ======================================================================

    private RefinementTask(Program program, Wmm targetMemoryModel, Wmm baselineModel, WitnessGraph witness, Configuration config)
    throws InvalidConfigurationException {
        super(program, targetMemoryModel, witness, config);
        config.inject(this);
        this.baselineModel = baselineModel != null ? baselineModel : createDefaultWmm();
    }

    public Wmm getBaselineModel() {
        return baselineModel;
    }

    public WmmEncoder getBaselineWmmEncoder() { return baselineWmmEncoder; }

    @Override
    public void performStaticWmmAnalyses() throws InvalidConfigurationException {
        super.performStaticWmmAnalyses();
        VerificationTask newTask = new VerificationTask(getProgram(), baselineModel, getWitness(), getConfig());
        baselineContext = Context.createCopyFrom(newTask,getAnalysisContext());
        baselineContext.invalidate(WmmAnalysis.class);
        baselineContext.register(WmmAnalysis.class, WmmAnalysis.fromConfig(baselineModel, getConfig()));
        baselineContext.register(RelationAnalysis.class, RelationAnalysis.fromConfig(newTask, baselineContext, getConfig()));
    }

    @Override
    public void initializeEncoders(SolverContext ctx) throws InvalidConfigurationException {
        progEncoder = ProgramEncoder.fromConfig(getProgram(), getAnalysisContext(), getConfig());
        propertyEncoder = PropertyEncoder.fromConfig(getProgram(), getMemoryModel(), getAnalysisContext(), getConfig());
        //wmmEncoder = WmmEncoder.fromConfig(getMemoryModel(), getAnalysisContext(), getConfig());
        symmetryEncoder = SymmetryEncoder.fromConfig(getMemoryModel(), getAnalysisContext(), getConfig());
        baselineWmmEncoder = WmmEncoder.fromConfig(baselineModel, baselineContext, getConfig());

        progEncoder.initializeEncoding(ctx);
        propertyEncoder.initializeEncoding(ctx);
        //wmmEncoder.initializeEncoding(ctx);
        symmetryEncoder.initializeEncoding(ctx);
        baselineWmmEncoder.initializeEncoding(ctx);
		logger.info("{}: {}", BASELINE, baselines);
    }

    public static RefinementTask fromVerificationTaskWithDefaultBaselineWMM(RegularVerificationTask task)
            throws InvalidConfigurationException {
        return new RefinementTaskBuilder()
                .withWitness(task.getWitness())
                .withConfig(task.getConfig())
                .build(task.getProgram(), task.getMemoryModel());
    }

    private Wmm createDefaultWmm() {
        Wmm baseline = new Wmm();
        RelationRepository repo = baseline.getRelationRepository();
        Relation rf = repo.getRelation(RF);

        if(baselines.contains(UNIPROC)) {
	        // ---- acyclic(po-loc | rf) ----
	        Relation poloc = repo.getRelation(POLOC);
	        Relation co = repo.getRelation(CO);
	        Relation fr = repo.getRelation(FR);
	        Relation porf = repo.union(poloc, rf);
	        Relation porfco =repo.union(porf, co);
	        Relation porfcofr = repo.union(porfco, fr);
	        baseline.addAxiom(new Acyclic(porfcofr));
        }
        if(baselines.contains(NO_OOTA)) {
            // ---- acyclic (dep | rf) ----
            Relation data = repo.getRelation(DATA);
            Relation ctrl = repo.getRelation(CTRL);
            Relation addr = repo.getRelation(ADDR);
            Relation dep = repo.union(data, addr);
            Relation hb = repo.union(repo.union(ctrl, dep), rf);
            baseline.addAxiom(new Acyclic(hb));
        }
        if(baselines.contains(ATOMIC_RMW)) {
    		// ---- empty (rmw & fre;coe) ----
            Relation rmw = repo.getRelation(RMW);
            Relation coe = repo.getRelation(COE);
            Relation fre = repo.getRelation(FRE);
            Relation frecoe = repo.composition(fre, coe);
            Relation rmwANDfrecoe = repo.intersection(rmw, frecoe);
            baseline.addAxiom(new Empty(rmwANDfrecoe));
        }
        
        return baseline;
    }

    // ==================== Builder =====================

    private static final class RefinementTaskBuilder extends VerificationTaskBuilder<RefinementTask> {

        private Wmm baselineModel;

        public RefinementTaskBuilder withBaselineWMM(Wmm baselineModel) {
            this.baselineModel = baselineModel;
            return this;
        }

        @Override
        public RefinementTask build(Program program, Wmm memoryModel) throws InvalidConfigurationException {
            return new RefinementTask(program, memoryModel, baselineModel, witness, config.build());
        }
    }
}