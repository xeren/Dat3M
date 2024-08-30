package com.dat3m.dartagnan.solver.caat4wmm;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.Model;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.program.filter.Filter;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.model.ExecutionModel;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;

public class GenMCWMMSolver {
    
    private final GenMCExecutionGraph executionGraph;
    private final ExecutionModel executionModel;
    private final CAATSolver solver;

    private GenMCWMMSolver(Wmm memoryModel, Context analysisContext, ExecutionModel m) {
        final RelationAnalysis ra = analysisContext.requires(RelationAnalysis.class);
        this.executionGraph = new GenMCExecutionGraph(memoryModel, ra);
        this.executionModel = m;
        this.solver = CAATSolver.create();
    }

    public static GenMCWMMSolver withContext(Wmm memoryModel, EncodingContext context, Context analysisContext) throws InvalidConfigurationException {
        return new GenMCWMMSolver(memoryModel, analysisContext, ExecutionModel.withContext(context));
    }

    public ExecutionModel getExecution() {
        return executionModel;
    }

    public GenMCExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    public Result check(Model model, Filter filter) {
        // ============ Extract ExecutionModel ==============
        long curTime = System.currentTimeMillis();
        executionModel.initialize(model, filter, true);
        executionGraph.initializeFromModel(executionModel);
        long extractTime = System.currentTimeMillis() - curTime;

        // ============== Run the CAATSolver ==============
        CAATSolver.Result caatResult = solver.simpleCheck(executionGraph.getCAATModel());
        Result result = Result.fromCAATResult(caatResult);
        Statistics stats = result.stats;
        stats.modelExtractionTime = extractTime;
        stats.modelSize = executionGraph.getDomain().size();

        return result;
    }

     // ===================== Classes ======================

     public static class Result {
        private CAATSolver.Status status;
        private Statistics stats;

        public CAATSolver.Status getStatus() { return status; }
        public Statistics getStatistics() { return stats; }

        Result() {
            status = CAATSolver.Status.INCONCLUSIVE;
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
