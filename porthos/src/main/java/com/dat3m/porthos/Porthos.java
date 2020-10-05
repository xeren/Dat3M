package com.dat3m.porthos;

import com.dat3m.dartagnan.Dartagnan;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.porthos.utils.options.PorthosOptions;
import com.microsoft.z3.*;
import com.microsoft.z3.enumerations.Z3_ast_print_mode;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Graph;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static com.dat3m.porthos.Encodings.encodeCommonExecutions;
import static com.dat3m.porthos.Encodings.encodeReachedState;

public class Porthos {

	public static void main(String[] args) throws IOException {

		PorthosOptions options = new PorthosOptions();
		try {
			options.parse(args);
		} catch(Exception e) {
			if(e instanceof UnsupportedOperationException) {
				System.out.println(e.getMessage());
			}
			new HelpFormatter().printHelp("PORTHOS", options);
			System.exit(1);
			return;
		}

		Wmm mcmS = new ParserCat().parse(new File(options.getSourceModelFilePath()));
		Wmm mcmT = new ParserCat().parse(new File(options.getTargetModelFilePath()));

		ProgramParser programParser = new ProgramParser();
		Program pSource = programParser.parse(new File(options.getProgramFilePath()));
		Program pTarget = programParser.parse(new File(options.getProgramFilePath()));

		try(EncodeContext context = new EncodeContext(Settings.TACTIC)) {

			Arch source = options.getSource();
			Arch target = options.getTarget();
			Settings settings = options.getSettings();
			System.out.println("Settings: " + options.getSettings());

			PorthosResult result = testProgram(context, pSource, pTarget, source, target, mcmS, mcmT, settings);

			if(result.getIsPortable()) {
				System.out.println("The program is state-portable");
				System.out.println("Iterations: " + result.getIterations());

			} else {
				System.out.println("The program is not state-portable");
				System.out.println("Iterations: " + result.getIterations());
				if(settings.getDrawGraph()) {
					context.context.setPrintMode(Z3_ast_print_mode.Z3_PRINT_SMTLIB_FULL);
					Dartagnan.drawGraph(new Graph(context, context.model().orElseThrow(), pSource, pTarget, settings.getGraphRelations()), options.getGraphFilePath());
					System.out.println("Execution graph is written to " + options.getGraphFilePath());
				}
			}
		}
	}

	public static PorthosResult testProgram(EncodeContext context,
		Program pSource, Program pTarget,
		Arch source, Arch target,
		Wmm sourceWmm, Wmm targetWmm,
		Settings settings) {

		pSource.unroll(settings.getBound(), 0);
		pTarget.unroll(settings.getBound(), 0);

		int nextId = pSource.compile(source, 0);
		pTarget.compile(target, nextId);

		ProgramCache cCache = new ProgramCache(pSource);
		pSource.encodeCF(context);
		pSource.encodeFinalRegisterValues(context);
		sourceWmm.encode(context, cCache, settings);
		Solver s = context.solver();

		ProgramCache cTarget = new ProgramCache(pTarget);
		pTarget.encodeCF(context);
		pTarget.encodeFinalRegisterValues(context);
		targetWmm.encode(context, cTarget, settings);
		context.rule(targetWmm.consistent(context));
		context.rule(sourceWmm.inconsistent(context));
		context.rule(encodeCommonExecutions(context, pTarget, pSource));

		s.add(sourceWmm.consistent(context));

		int iterations = 1;
		for(Optional<Model> model; (model = context.model()).isPresent();) {
			BoolExpr reachedState = encodeReachedState(pTarget, model.get(), context.context);
			if(s.check(reachedState) == Status.UNSATISFIABLE)
				return new PorthosResult(false, iterations, pSource, pTarget);
			context.rule(context.not(reachedState));
			iterations++;
		}
		return new PorthosResult(true, iterations, pSource, pTarget);
	}
}