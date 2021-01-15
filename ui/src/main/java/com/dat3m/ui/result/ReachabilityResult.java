package com.dat3m.ui.result;

import static com.dat3m.dartagnan.analysis.Base.*;
import static com.dat3m.dartagnan.utils.Result.FAIL;

import com.dat3m.dartagnan.Dartagnan;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Graph;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.ui.utils.UiOptions;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;

public class ReachabilityResult implements Dat3mResult {

	private final Program program;
	private final Wmm wmm;
	private final UiOptions options;

	private Graph graph;
	private String verdict;

	public ReachabilityResult(Program program, Wmm wmm, UiOptions options) {
		this.program = program;
		this.wmm = wmm;
		this.options = options;
		run();
	}

	public Graph getGraph() {
		return graph;
	}

	public String getVerdict() {
		return verdict;
	}

	private void run() {
		if(validate()) {
			Context ctx = new Context();
			Solver solver = ctx.mkSolver();
			Result result = options.getUseCore()
				? runRefining(solver, ctx, program, wmm, options.getSettings())
				: runAnalysis(solver, ctx, program, wmm, options.getSettings());
			buildVerdict(result);
			if(options.getSettings().getDrawGraph() && Dartagnan.canDrawGraph(program.getAss(), result == FAIL)) {
				graph = new Graph(solver.getModel(), ctx, program, options.getSettings().getGraphRelations());
			}
			ctx.close();
		}
	}

	private void buildVerdict(Result result) {
		verdict = "Condition " + program.getAss().toStringWithType() + "\n" + result + "\n";
	}

	private boolean validate() {
		return true;
	}
}
