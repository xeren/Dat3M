package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.SolverContext;

import static com.dat3m.dartagnan.encoding.ProgramEncoder.dependencyEdgeVariable;

import static com.dat3m.dartagnan.encoding.ProgramEncoder.execution;

abstract class BasicRegRelation extends StaticRelation {

    @Override
    public BooleanFormula getSMTVar(Tuple t, SolverContext ctx) {
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        BooleanFormulaManager bmgr = ctx.getFormulaManager().getBooleanFormulaManager();
        return minTupleSet.contains(t) ?
                execution(t.getFirst(), t.getSecond(), exec, ctx) :
        		maxTupleSet.contains(t) ?
                        dependencyEdgeVariable(t.getFirst(), t.getSecond(), bmgr) :
        				bmgr.makeFalse();
    }
}