package com.dat3m.ui.result;

import static com.dat3m.dartagnan.utils.Result.FAIL;

import com.dat3m.dartagnan.Dartagnan;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Graph;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.ui.utils.UiOptions;
import com.dat3m.ui.utils.Utils;

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
			try(EncodeContext context = new EncodeContext()) {
				Result result = Dartagnan.testProgram(context, new ProgramCache(program), wmm, options.getTarget(), options.getSettings());
				buildVerdict(result);
				if(options.getSettings().getDrawGraph() && Dartagnan.canDrawGraph(program.getAss(), result == FAIL)) {
					graph = new Graph(context, context.model().orElseThrow(), program, options.getSettings().getGraphRelations());
				}
			}
		}
	}

	private void buildVerdict(Result result) {
		verdict = "Condition " + program.getAss().toStringWithType() + "\n" + result + "\n";
	}

	private boolean validate() {
		Arch target = program.getArch() == null ? options.getTarget() : program.getArch();
		if(target == null) {
			Utils.showError("Missing target architecture.");
			return false;
		}
		program.setArch(target);
		return true;
	}
}
