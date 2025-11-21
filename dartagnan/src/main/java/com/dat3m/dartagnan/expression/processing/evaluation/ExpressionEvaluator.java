package com.dat3m.dartagnan.expression.processing.evaluation;

import java.math.BigInteger;
import java.util.Map;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.VerificationTask;
import com.dat3m.dartagnan.verification.model.EventData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;
import org.sosy_lab.java_smt.api.SolverException;

public interface ExpressionEvaluator {

    ExpressionEvaluator newInstance();
    
    BigInteger evaluate(Expression expr, Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException;
    BigInteger evaluateNonDet(Expression expr, Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException;
    BigInteger evaluateAddress(EventData e) throws SolverException, InterruptedException, InvalidConfigurationException;
    boolean evaluateReachability(BooleanFormula formula) throws SolverException, InterruptedException, InvalidConfigurationException;
    boolean evaluateReachability(Expression target, Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException;

    void setTrace(GenMCEventDomain trace, Event changedEvent);
    void addNonDetCondition(Event event, Expression condition, boolean isTrue) throws InvalidConfigurationException;

    int getEvaluationTime();
    int getSolverCheckTime();
    int getSolverChecks();

    static BigInteger getValue(Object value) {
        String valueString = String.valueOf(value);
        return switch(valueString) {
            // NULL case can happen if the solver optimized away a variable.
            // This should only happen if the value is irrelevant, so we will just pick 0.
            case "false", "null" -> BigInteger.ZERO;
            case "true" -> BigInteger.ONE;
            default -> new BigInteger(valueString);
        };
    }

    static ExpressionEvaluator withContext(ExprEvaluationContext evalContext) throws InvalidConfigurationException {
        if (evalContext.usesSMTSolver()) {
            return new ProverExpressionEvaluator(evalContext.getProver(), evalContext.getTraceEncoder());
        } else {
            return new ConstantPropagationExpressionEvaluator(evalContext);
        }
    }

    class ExprEvaluationContext {
        private static final Logger logger = LogManager.getLogger(ExprEvaluationContext.class);

        private final VerificationTask task;
        private final Context analysisContext;
        private final SolverContext ctx;
        private final ProverEnvironment prover;
        private final boolean useSMTSolver;

        private GenMCTraceEncoder traceEncoder;
        private BooleanFormula properties;

        public ExprEvaluationContext(VerificationTask task, Context analysisContext, SolverContext ctx, ProverEnvironment prover, boolean useSMTSolver) {
            this.task = task;
            this.analysisContext = analysisContext;
            this.ctx = ctx;
            this.prover = prover;
            this.useSMTSolver = useSMTSolver;
        }

        protected Map<MemoryObject, Expression> getMemoryLayout() {
            return GenMCTraceEncoder.getMemoryLayout(task.getProgram().getMemory());
        }
        public GenMCTraceEncoder getTraceEncoder() throws InvalidConfigurationException {
            if (!neededSMTSolver()) {
                EncodingContext context = EncodingContext.of(task, analysisContext, ctx.getFormulaManager());
                logger.info("Used SMT solver: {}", ctx.getVersion());
                traceEncoder = GenMCTraceEncoder.withContext(context);
            }
            return traceEncoder;
        }
        public boolean neededSMTSolver() {
            return traceEncoder != null;
        }
        public BooleanFormula getProperties() throws InvalidConfigurationException {
            if (properties == null) {
                properties = task.getProgram().getFormat() == Program.SourceLanguage.LITMUS ?
                        PropertyEncoder.withContext(getTraceEncoder().getEncodingContext()).encodeProperties(task.getProperty()) : null;
            }
            return properties;
        }
        protected boolean usesSMTSolver() {
            return useSMTSolver;
        }
        protected ProverEnvironment getProver() {
            return prover;
        }
    }
}
