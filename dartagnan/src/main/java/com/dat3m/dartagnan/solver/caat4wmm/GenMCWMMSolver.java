package com.dat3m.dartagnan.solver.caat4wmm;

import java.util.Set;

import com.dat3m.dartagnan.configuration.GenMCReasoning;
import com.dat3m.dartagnan.solver.caat.predicates.CAATPredicate;
import com.dat3m.dartagnan.solver.caat.reasoning.CAATLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc.AbstractGenMCWMMPredicate;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.CoreLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.GenMCCoreReasoner;
import com.dat3m.dartagnan.utils.logic.Conjunction;
import com.dat3m.dartagnan.utils.logic.DNF;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.wmm.Wmm;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;

public class GenMCWMMSolver {
    
    private final GenMCExecutionGraph executionGraph;
    private final CAATSolver solver;
    private final GenMCCoreReasoner reasoner;
    private Extractor extractor;

    private GenMCWMMSolver(final Configuration config, final Wmm memoryModel, final Context analysisContext) throws InvalidConfigurationException {
        this.executionGraph = new GenMCExecutionGraph(memoryModel);
        this.reasoner = GenMCCoreReasoner.fromConfig(config, analysisContext, executionGraph);
        this.solver = CAATSolver.create();
    }

    public static GenMCWMMSolver fromConfig(final Configuration config, final Wmm memoryModel, final Context analysisContext) throws InvalidConfigurationException {
        return new GenMCWMMSolver(config, memoryModel, analysisContext);
    }

    public void injectExtractor(final Extractor extractor) {
        this.extractor = extractor;
    }

    public GenMCExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    public Result check(final GenMCEventDomain trace, final GenMCReasoning reasoningMethod) {
        // ============ Extract GenMCEventDomain ==============
        executionGraph.initializeFromDomain(trace);

        // ============== Run the CAATSolver ==============
        final boolean useReasoning = reasoningMethod != GenMCReasoning.NONE;
        final CAATSolver.Result caatResult = useReasoning ? solver.check(executionGraph.getCAATModel()) : solver.simpleCheck(executionGraph.getCAATModel());
        final Result result = Result.fromCAATResult(caatResult);

        if (useReasoning && result.getStatus() == CAATSolver.Status.INCONSISTENT) {
            // ============== Compute Core reasons ==============
            final DNF<CAATLiteral> baseReasons = caatResult.getBaseReasons();
            if (reasoningMethod.usesPatterns()) {
                extractor.extract(baseReasons);
            }
            if (reasoningMethod.usesReasons()) {
                final Set<Conjunction<CoreLiteral>> coreReasons = reasoner.toCoreReasons(baseReasons);
                result.coreReasons = new DNF<>(coreReasons);
            }
        }

        return result;
    }

    public void updateBasePredicates(final GenMCEventDomain trace) {
        AbstractGenMCWMMPredicate.allowInit();
        for (final CAATPredicate basePredicate : executionGraph.getCAATModel().getHierarchy().getBasePredicates()) {
            basePredicate.initializeToDomain(trace);
            basePredicate.repopulate();
        }
        AbstractGenMCWMMPredicate.forbidInit();
    }

     // ===================== Classes ======================

     public static class Result {
        private CAATSolver.Status status;
        private DNF<CoreLiteral> coreReasons;

        public CAATSolver.Status getStatus() { return status; }
        public DNF<CoreLiteral> getCoreReasons() { return coreReasons; }

        Result() {
            status = CAATSolver.Status.INCONCLUSIVE;
            coreReasons = DNF.FALSE();
        }

        static Result fromCAATResult(final CAATSolver.Result caatResult) {
            final Result result = new Result();
            result.status = caatResult.getStatus();

            return result;
        }

        @Override
        public String toString() {
            return status + "\n" + coreReasons;
        }
    }
}
