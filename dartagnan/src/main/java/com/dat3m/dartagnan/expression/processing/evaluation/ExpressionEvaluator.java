package com.dat3m.dartagnan.expression.processing.evaluation;

import java.math.BigInteger;

import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.encoding.GenMCTraceEncoder;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.verification.model.EventData;

public interface ExpressionEvaluator {

    ExpressionEvaluator newInstance();
    
    BigInteger evaluate(Expression expr) throws SolverException, InterruptedException;
    BigInteger evaluateNonDet(Expression expr) throws SolverException, InterruptedException;
    BigInteger evaluateAddress(EventData e) throws SolverException, InterruptedException;
    boolean evaluateReachability(BooleanFormula formula) throws SolverException, InterruptedException;
    boolean evaluateReachability(Expression target) throws SolverException, InterruptedException;

    void setTrace(GenMCEventDomain trace, Event changedEvent);
    void addNonDetCondition(Event event, Expression condition, boolean isTrue);

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

    public static ExpressionEvaluator getInstance(EncodingContext encodingContext, ProverEnvironment prover, GenMCTraceEncoder traceEncoder, boolean useSMTSolver) {
        if (useSMTSolver) {
            return new ProverExpressionEvaluator(encodingContext, prover, traceEncoder);
        } else {
            return new ConstantPropagationExpressionEvaluator(encodingContext, prover, traceEncoder);
        }
    }
}
