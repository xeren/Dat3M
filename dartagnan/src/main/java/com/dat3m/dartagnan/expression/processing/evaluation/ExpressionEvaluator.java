package com.dat3m.dartagnan.expression.processing.evaluation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.encoding.PropertyEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.utils.Timer;
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

public abstract class ExpressionEvaluator {

    protected static final Timer evaluationTimer = new Timer();
    protected static final Timer solverCheckTimer = new Timer();
    protected static int solverChecks = 0;
    protected GenMCEventDomain trace;

    protected final Map<Event, BooleanFormula> cfContext = new HashMap<>();

    public abstract ExpressionEvaluator newInstance();

    public abstract BigInteger evaluate(final Expression expr, final Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException;
    public abstract BigInteger evaluateNonDet(final Expression expr, final Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException;
    public abstract BigInteger evaluateAddress(final EventData e) throws SolverException, InterruptedException, InvalidConfigurationException;
    public abstract boolean evaluateReachability(final BooleanFormula formula) throws SolverException, InterruptedException, InvalidConfigurationException;
    public abstract boolean evaluateReachability(final Expression target, final Event forEvent) throws SolverException, InterruptedException, InvalidConfigurationException;

    public void setTrace(final GenMCEventDomain trace, final EventData changedEvent, final boolean inTraceOrder) {
        this.trace = trace;
        List<Event> cfEventsToRemove = new ArrayList<>();
        for (Event cfEvent : cfContext.keySet()) {
            if (trace.getData(cfEvent).isEmpty()) {
                cfEventsToRemove.add(cfEvent);
            }
        }
        cfEventsToRemove.forEach(cfContext::remove);
    }
    public abstract void addNonDetCondition(final Event event, final Expression condition, final boolean isTrue) throws InvalidConfigurationException;

    public Timer getEvaluationTime() {
        return Timer.add(solverCheckTimer, evaluationTimer);
    }

    public Timer getSolverCheckTime() {
        return solverCheckTimer;
    }

    public int getSolverChecks() {
        return solverChecks;
    }

    public static BigInteger getValue(final Object value) {
        final String valueString = String.valueOf(value);
        return switch(valueString) {
            // NULL case can happen if the solver optimized away a variable.
            // This should only happen if the value is irrelevant, so we will just pick 0.
            case "false", "null" -> BigInteger.ZERO;
            case "true" -> BigInteger.ONE;
            default -> new BigInteger(valueString);
        };
    }

    public static ExpressionEvaluator withContext(final ExprEvaluationContext evalContext) throws InvalidConfigurationException {
        initMemoryLayout(evalContext);
        if (evalContext.usesSMTSolver()) {
            return new ProverExpressionEvaluator(evalContext.getProver(), evalContext.getTraceEncoder());
        } else {
            return new ConstantPropagationExpressionEvaluator(evalContext);
        }
    }

    private static void initMemoryLayout(final ExprEvaluationContext evalContext) {
        for (final Map.Entry<MemoryObject, Expression> address : evalContext.getMemoryLayout().entrySet()) {
            final MemoryObject addressObject = address.getKey();
            if (addressObject.getAddress() == null) {
                addressObject.setAddress(address.getValue());
            }
        }
    }

    public static class ExprEvaluationContext {
        private static final Logger logger = LogManager.getLogger(ExprEvaluationContext.class);

        private final VerificationTask task;
        private final Context analysisContext;
        private final SolverContext ctx;
        private final ProverEnvironment prover;
        private final boolean useSMTSolver;

        private GenMCTraceEncoder traceEncoder;
        private BooleanFormula properties;

        public ExprEvaluationContext(final VerificationTask task, final Context analysisContext, final SolverContext ctx, final ProverEnvironment prover,
                                     final boolean useSMTSolver) {
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
                final EncodingContext context = EncodingContext.of(task, analysisContext, ctx.getFormulaManager());
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
