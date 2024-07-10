package com.dat3m.dartagnan.solver.caat4wmm;

import org.sosy_lab.common.configuration.InvalidConfigurationException;

import com.dat3m.dartagnan.solver.caat.CAATSolver;
import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.verification.model.EventData;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;

public class GenMCWMMSolver {
    
    private final GenMCExecutionGraph executionGraph;
    private final CAATSolver solver;

    private GenMCWMMSolver(Wmm memoryModel, Context analysisContext) {
        final RelationAnalysis ra = analysisContext.requires(RelationAnalysis.class);
        this.executionGraph = new GenMCExecutionGraph(memoryModel, ra);
        this.solver = CAATSolver.create();
    }

    public static GenMCWMMSolver withContext(Wmm memoryModel, Context analysisContext) throws InvalidConfigurationException {
        return new GenMCWMMSolver(memoryModel, analysisContext);
    }

    public GenMCExecutionGraph getExecutionGraph() {
        return executionGraph;
    }

    public CAATSolver.Status check(Domain<EventData> domain) {
        executionGraph.initializeFromDomain(domain);

        // ============== Run the CAATSolver ==============
        CAATSolver.Result caatResult = solver.check(executionGraph.getCAATModel());

        return caatResult.getStatus();
    }
}
