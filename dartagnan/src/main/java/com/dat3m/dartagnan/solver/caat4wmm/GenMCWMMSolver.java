package com.dat3m.dartagnan.solver.caat4wmm;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.dat3m.dartagnan.configuration.GenMCReasoning;
import com.dat3m.dartagnan.solver.caat.predicates.CAATPredicate;
import com.dat3m.dartagnan.solver.caat.predicates.sets.SetPredicate;
import com.dat3m.dartagnan.solver.caat.reasoning.CAATLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.basePredicates.genmc.AbstractGenMCWMMPredicate;
import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.CoreLiteral;
import com.dat3m.dartagnan.solver.caat4wmm.coreReasoning.GenMCCoreReasoner;
import com.dat3m.dartagnan.utils.logic.Conjunction;
import com.dat3m.dartagnan.utils.logic.DNF;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.wmm.Wmm;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;

import static com.dat3m.dartagnan.wmm.RelationNameRepository.CO;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.RF;

public class GenMCWMMSolver {
    
    private final GenMCExecutionGraph executionGraph;
    private final CAATSolver solver;
    private final GenMCCoreReasoner reasoner;
    private final PatternManager patterns;
    private final GenMCReasoning reasoningMethod;
    private final AbstractGenMCWMMPredicate[] basePredicates;

    private GenMCWMMSolver(final VerificationTask task, final Context analysisContext, final GenMCReasoning reasoningMethod)
            throws InvalidConfigurationException {
        final Wmm memoryModel = task.getMemoryModel();
        final Configuration config = task.getConfig();
        this.executionGraph = new GenMCExecutionGraph(memoryModel);
        this.reasoner = reasoningMethod.usesReasons() ? GenMCCoreReasoner.fromConfig(config, analysisContext, executionGraph) : null;
        this.solver = CAATSolver.create();
        this.patterns = reasoningMethod.usesPatterns() ? PatternManager.fromConfig(config, executionGraph, memoryModel.getRelation(RF), memoryModel.getRelation(CO)) : null;
        this.reasoningMethod = reasoningMethod;
        final Set<CAATPredicate> basePredicates = executionGraph.getCAATModel().getHierarchy().getBasePredicates();
        this.basePredicates = new AbstractGenMCWMMPredicate[basePredicates.size()];
        int i = -1;
        for (final CAATPredicate pred : basePredicates) {
            this.basePredicates[++i] = (AbstractGenMCWMMPredicate) pred;
        }
    }

    public static GenMCWMMSolver withTask(final VerificationTask task, final Context analysisContext, final GenMCReasoning reasoningMethod)
            throws InvalidConfigurationException {
        return new GenMCWMMSolver(task, analysisContext, reasoningMethod);
    }

    public GenMCExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    public PatternManager getPatterns() {
        return patterns;
    }

    public Result check(final GenMCEventDomain trace) {
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
                result.backtrackingPoint = patterns.addFromBaseReasons(baseReasons);
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
        for (final AbstractGenMCWMMPredicate basePredicate : basePredicates) {
            if (initSetPredicatesForPatterns() || !(basePredicate instanceof SetPredicate)) {
                basePredicate.initializeToDomain(trace);
                basePredicate.repopulate();
            }
        }
        AbstractGenMCWMMPredicate.forbidInit();
    }

    public void updateBasePredicates(final GenMCEventDomain trace, final Collection<EventData> addedEvents) {
        AbstractGenMCWMMPredicate.allowInit();
        for (final AbstractGenMCWMMPredicate basePredicate : basePredicates) {
            if (initSetPredicatesForPatterns() || !(basePredicate instanceof SetPredicate)) {
                basePredicate.initializeToDomain(trace);
                basePredicate.forwardPropagate(addedEvents);
            }
        }
        AbstractGenMCWMMPredicate.forbidInit();
    }

    public void updateRemainingBasePredicates(final GenMCEventDomain trace) {
        AbstractGenMCWMMPredicate.allowInit();
        for (final AbstractGenMCWMMPredicate basePredicate : basePredicates) {
            if (!initSetPredicatesForPatterns() && basePredicate instanceof SetPredicate) {
                basePredicate.initializeToDomain(trace);
                basePredicate.repopulate();
            }
        }
        AbstractGenMCWMMPredicate.forbidInit();
    }

    public void updateRemainingBasePredicates(final GenMCEventDomain trace, final Collection<EventData> addedEvents) {
        if (!reasoningMethod.usesPatterns()) {
            updateBasePredicates(trace, addedEvents);
        }
        AbstractGenMCWMMPredicate.allowInit();
        for (final AbstractGenMCWMMPredicate basePredicate : basePredicates) {
            if (!initSetPredicatesForPatterns() && basePredicate instanceof SetPredicate) {
                basePredicate.initializeToDomain(trace);
                basePredicate.forwardPropagate(addedEvents);
            }
        }
        AbstractGenMCWMMPredicate.forbidInit();
    }

    private boolean initSetPredicatesForPatterns() {
        return patterns != null && patterns.useSets();
    }

     // ===================== Classes ======================

     public static class Result {
        private CAATSolver.Status status;
        private DNF<CoreLiteral> coreReasons;
        private Map.Entry<EventData, Boolean> backtrackingPoint;

        public CAATSolver.Status getStatus() { return status; }
        public DNF<CoreLiteral> getCoreReasons() { return coreReasons; }
        public Map.Entry<EventData, Boolean> getBacktrackingPoint() { return backtrackingPoint; }

        Result() {
            status = CAATSolver.Status.INCONCLUSIVE;
            coreReasons = DNF.FALSE();
            backtrackingPoint = null;
        }

        static Result fromCAATResult(final CAATSolver.Result caatResult) {
            final Result result = new Result();
            result.status = caatResult.getStatus();

            return result;
        }

        @Override
        public String toString() {
            return status + "\n" + coreReasons + "\n" + backtrackingPoint;
        }
    }
}
