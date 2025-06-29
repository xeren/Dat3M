package com.dat3m.dartagnan.solver.caat4wmm;

import java.util.Set;

import com.dat3m.dartagnan.configuration.GenMCReasoning;
import com.dat3m.dartagnan.solver.caat.predicates.CAATPredicate;
import com.dat3m.dartagnan.solver.caat.reasoning.CAATLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc.AbstractGenMCWMMPredicate;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.CoreLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.GenMCCoreReasoner;
import com.dat3m.dartagnan.utils.logic.Conjunction;
import com.dat3m.dartagnan.utils.logic.DNF;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;

import org.sosy_lab.common.configuration.InvalidConfigurationException;

import static com.dat3m.dartagnan.wmm.RelationNameRepository.CO;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.RF;

public class GenMCWMMSolver {
    
    private final GenMCExecutionGraph executionGraph;
    private final CAATSolver solver;
    private final GenMCCoreReasoner reasoner;
    private Extractor extractor;

    private final Relation rf;
    private final Relation co;

    private GenMCWMMSolver(Wmm memoryModel, Context analysisContext) {
        final RelationAnalysis ra = analysisContext.requires(RelationAnalysis.class);
        this.executionGraph = new GenMCExecutionGraph(memoryModel, ra);
        this.reasoner = new GenMCCoreReasoner(analysisContext, executionGraph);
        this.solver = CAATSolver.create();
        this.rf = memoryModel.getRelation(RF);
        this.co = memoryModel.getRelation(CO);
    }

    public static GenMCWMMSolver withContext(Wmm memoryModel, Context analysisContext) throws InvalidConfigurationException {
        return new GenMCWMMSolver(memoryModel, analysisContext);
    }

    public void injectExtractor(Extractor extractor) {
        this.extractor = extractor;
    }

    public GenMCExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    public Relation getRf() {
        return rf;
    }

    public Relation getCo() {
        return co;
    }

    public Result check(GenMCEventDomain trace, GenMCReasoning reasoningMethod) {
        // ============ Extract GenMCEventDomain ==============
        long curTime = System.currentTimeMillis();
        executionGraph.initializeFromDomain(trace);
        long extractTime = System.currentTimeMillis() - curTime;

        // ============== Run the CAATSolver ==============
        boolean useReasoning = reasoningMethod != GenMCReasoning.NONE;
        CAATSolver.Result caatResult = useReasoning ? solver.check(executionGraph.getCAATModel()) : solver.simpleCheck(executionGraph.getCAATModel());
        Result result = Result.fromCAATResult(caatResult);
        Statistics stats = result.stats;
        stats.modelExtractionTime = extractTime;
        stats.modelSize = executionGraph.getDomain().size();

        if (useReasoning && result.getStatus() == CAATSolver.Status.INCONSISTENT) {
            // ============== Compute Core reasons ==============
            DNF<CAATLiteral> baseReasons = caatResult.getBaseReasons();
            if (reasoningMethod.usesPatterns()) {
                extractor.extract(baseReasons);
            }
            if (reasoningMethod.usesReasons()) {
                Set<Conjunction<CoreLiteral>> coreReasons = reasoner.toCoreReasons(baseReasons);
                result.coreReasons = new DNF<>(coreReasons);
            }
        }

        return result;
    }

    public void updateBasePredicates(GenMCEventDomain trace) {
        AbstractGenMCWMMPredicate.allowInit();
        for (CAATPredicate basePredicate : executionGraph.getCAATModel().getHierarchy().getBasePredicates()) {
            basePredicate.initializeToDomain(trace);
            basePredicate.repopulate();
        }
        AbstractGenMCWMMPredicate.forbidInit();
    }

     // ===================== Classes ======================

     public static class Result {
        private CAATSolver.Status status;
        private DNF<CoreLiteral> coreReasons;
        private Statistics stats;

        public CAATSolver.Status getStatus() { return status; }
        public DNF<CoreLiteral> getCoreReasons() { return coreReasons; }
        public Statistics getStatistics() { return stats; }

        Result() {
            status = CAATSolver.Status.INCONCLUSIVE;
            coreReasons = DNF.FALSE();
        }

        static Result fromCAATResult(CAATSolver.Result caatResult) {
            Result result = new Result();
            result.status = caatResult.getStatus();
            result.stats = new Statistics();
            result.stats.caatStats = caatResult.getStatistics();

            return result;
        }

        @Override
        public String toString() {
            return status + "\n" +
                    coreReasons + "\n" +
                    stats;
        }
    }

    public static class Statistics {
        CAATSolver.Statistics caatStats;
        long modelExtractionTime;
        int modelSize;

        public long getModelExtractionTime() { return modelExtractionTime; }
        public long getPopulationTime() { return caatStats.getPopulationTime(); }
        public long getBaseReasonComputationTime() { return caatStats.getReasonComputationTime(); }
        public long getConsistencyCheckTime() { return caatStats.getConsistencyCheckTime(); }
        public int getModelSize() { return modelSize; }
        public int getNumComputedBaseReasons() { return caatStats.getNumComputedReasons(); }
        public int getNumComputedReducedBaseReasons() { return caatStats.getNumComputedReducedReasons(); }

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("Model extraction time(ms): ").append(getModelExtractionTime()).append("\n");
            str.append("Population time(ms): ").append(getPopulationTime()).append("\n");
            str.append("Consistency check time(ms): ").append(getConsistencyCheckTime()).append("\n");
            str.append("Base Reason computation time(ms): ").append(getBaseReasonComputationTime()).append("\n");
            str.append("Model size (#events): ").append(getModelSize()).append("\n");
            return str.toString();
        }
    }
}
