package com.dat3m.dartagnan;

import static com.dat3m.dartagnan.utils.Result.FAIL;
import static com.dat3m.dartagnan.utils.Result.PASS;
import static com.dat3m.dartagnan.utils.Result.BFAIL;
import static com.dat3m.dartagnan.utils.Result.BPASS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.dat3m.dartagnan.wmm.ProgramCache;
import org.apache.commons.cli.HelpFormatter;

import com.dat3m.dartagnan.asserts.AbstractAssert;
import com.dat3m.dartagnan.asserts.AssertTrue;
import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Graph;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.utils.options.DartagnanOptions;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.enumerations.Z3_ast_print_mode;

/**
 * Main entry point of this module.
 * Performs bounded model checking on a program with respect to a memory model.
 */
public class Dartagnan {

	public static void main(String[] args) throws IOException {

		DartagnanOptions options = new DartagnanOptions();
		try {
			options.parse(args);
		} catch(Exception e) {
			if(e instanceof UnsupportedOperationException) {
				System.out.println(e.getMessage());
			}
			new HelpFormatter().printHelp("DARTAGNAN", options);
			System.exit(1);
			return;
		}

		Wmm mcm = new ParserCat().parse(new File(options.getTargetModelFilePath()));
		Program p = new ProgramParser().parse(new File(options.getProgramFilePath()));

		Arch target = p.getArch();
		if(target == null) {
			target = options.getTarget();
		}
		if(target == null) {
			System.out.println("Compilation target cannot be inferred");
			System.exit(0);
			return;
		}

		Integer cegar = options.getCegar();
		if(cegar != null && cegar >= mcm.getAxioms().size()) {
			System.out.println("CEGAR argument must be between 1 and #axioms");
			System.exit(0);
			return;
		}

		Context ctx = new Context();
		Solver s = ctx.mkSolver();
		Settings settings = options.getSettings();

		Result result = cegar != null ? runCegar(s, ctx, p, mcm, target, settings, cegar) : testProgram(s, ctx, p, mcm, target, settings);

		if(options.getProgramFilePath().endsWith(".litmus")) {
			System.out.println("Settings: " + options.getSettings());
			if(p.getAssFilter() != null) {
				System.out.println("Filter " + (p.getAssFilter()));
			}
			System.out.println("Condition " + p.getAss().toStringWithType());
			System.out.println(result == Result.FAIL ? "Ok" : "No");
		} else {
			System.out.println(result);
		}

		if(settings.getDrawGraph() && canDrawGraph(p.getAss(), result.equals(FAIL))) {
			ctx.setPrintMode(Z3_ast_print_mode.Z3_PRINT_SMTLIB_FULL);
			drawGraph(new Graph(s.getModel(), ctx, p, settings.getGraphRelations()), options.getGraphFilePath());
			System.out.println("Execution graph is written to " + options.getGraphFilePath());
		}

		ctx.close();
	}

	public static Result testProgram(Solver s1, Context ctx, Program program, Wmm wmm, Arch target, Settings settings) {
		return testProgram(new EncodeContext(ctx, settings), new ProgramCache(program), s1, wmm, target);
	}

	public static Result testProgram(EncodeContext context, ProgramCache cache, Solver s1, Wmm wmm, Arch target) {

		Context ctx = context.context;
		Program program = cache.program;

		program.unroll(context.settings.getBound(), 0);

		program.compile(target, 0);

		// AssertionInline depends on compiled events (copies)
		// Thus we need to set the assertion after compilation
		if(program.getAss() == null) {
			AbstractAssert ass = program.createAssertion();
			program.setAss(ass);
			// Due to optimizations, the program might be trivially true
			// Not returning here might loop forever for cyclic programs
			if(ass instanceof AssertTrue) {
				return PASS;
			}
		}

		// Using two solvers is much faster than using
		// an incremental solver or check-sat-assuming
		Solver s2 = ctx.mkSolver();

		BoolExpr encodeUINonDet = program.encodeUINonDet(ctx);
		s1.add(encodeUINonDet);
		s2.add(encodeUINonDet);

		BoolExpr encodeCF = program.encodeCF(ctx);
		s1.add(encodeCF);
		s2.add(encodeCF);

		BoolExpr encodeFinalRegisterValues = program.encodeFinalRegisterValues(ctx);
		s1.add(encodeFinalRegisterValues);
		s2.add(encodeFinalRegisterValues);

		wmm.encode(context, new ProgramCache(program));
		BoolExpr encodeWmm = context.allRules();
		s1.add(encodeWmm);
		s2.add(encodeWmm);

		wmm.consistent(context);
		BoolExpr encodeConsistency = context.allRules();
		s1.add(encodeConsistency);
		s2.add(encodeConsistency);

		s1.add(program.getAss().encode(ctx));
		if(program.getAssFilter() != null) {
			BoolExpr encodeFilter = program.getAssFilter().encode(ctx);
			s1.add(encodeFilter);
			s2.add(encodeFilter);
		}

		BoolExpr encodeNoBoundEventExec = program.encodeNoBoundEventExec(ctx);

		Result res;
		if(s1.check() == Status.SATISFIABLE) {
			s1.add(encodeNoBoundEventExec);
			res = s1.check() == Status.SATISFIABLE ? FAIL : BFAIL;
		} else {
			s2.add(ctx.mkNot(encodeNoBoundEventExec));
			res = s2.check() == Status.SATISFIABLE ? BPASS : PASS;
		}

		if(program.getAss().getInvert()) {
			res = res.invert();
		}
		return res;
	}

	public static Result runCegar(Solver solver, Context ctx, Program program, Wmm wmm, Arch target, Settings settings, int cegar) {
		Map<BoolExpr, BoolExpr> track = new HashMap<>();
		program.unroll(settings.getBound(), 0);
		program.compile(target, 0);
		// AssertionInline depends on compiled events (copies)
		// Thus we need to set the assertion after compilation
		if(program.getAss() == null) {
			AbstractAssert ass = program.createAssertion();
			program.setAss(ass);
			// Due to optimizations, the program might be trivially true
			// Not returning here might loop forever for cyclic programs
			if (ass instanceof AssertTrue) {
				return PASS;
			}
		}

		EncodeContext e = new EncodeContext(ctx, settings);

		solver.add(program.encodeUINonDet(ctx));
		solver.add(program.encodeCF(ctx));
		solver.add(program.encodeFinalRegisterValues(ctx));
		ProgramCache p = new ProgramCache(program);
		wmm.encodeBase(e, p);
		wmm.getAxioms().get(cegar).encodeRelAndConsistency(e, p);
		solver.add(e.allRules());

		if(program.getAssFilter() != null)
			solver.add(program.getAssFilter().encode(ctx));

		// Termination guaranteed because we add a new constraint in each 
		// iteration and thus the formula will eventually become UNSAT
		Result res;
		while(true){
			solver.push();
			// This needs to be pop for the else branch below
			// If not the formula will always remain UNSAT
			solver.add(program.getAss().encode(ctx));
			if(solver.check() == Status.SATISFIABLE) {
				solver.push();
				solver.add(program.encodeNoBoundEventExec(ctx));
				res = solver.check() == Status.SATISFIABLE ? FAIL : BFAIL;
				solver.pop();
			} else {
				solver.pop();
				solver.push();
				solver.add(ctx.mkNot(program.encodeNoBoundEventExec(ctx)));
				res = solver.check() == Status.SATISFIABLE ? BPASS : PASS;
			}
			// We get rid of the formulas added in the above branches
			solver.pop();

			if(program.getAss().getInvert()) {
				res = res.invert();
			}

			// If we are not using CEGAR or the formula was UNSAT, we return
			if(cegar == -1 || res.equals(PASS) || res.equals(BPASS)) {
				return res;
			}

			solver.push();
			solver.add(program.getAss().encode(ctx));
			// We need this to get the model below. This check will always succeed
			// If not we would have returned above
			solver.check();
			BoolExpr execution = program.getRf(ctx, solver.getModel());
			solver.add(execution);
			wmm.encodeBase(e, new ProgramCache(program));
			solver.add(e.allRules());
			for(Axiom ax: wmm.getAxioms()) {
				ax.encodeRelAndConsistency(e, p);
				BoolExpr enc = e.allRules();
				BoolExpr axVar = ctx.mkBoolConst(ax.toString());
				solver.assertAndTrack(enc, axVar);
				track.put(axVar, enc);
			}

			if(solver.check() == Status.SATISFIABLE) {
				// For CEGAR, the same code above seems to never give BFAIL
				// Thus we add the constraint here to avoid FAIL when the unrolling was not enough
				solver.add(program.encodeNoBoundEventExec(ctx));
				return solver.check() == Status.SATISFIABLE ? FAIL : BFAIL;
			}

			BoolExpr[] unsatCore = solver.getUnsatCore();
			solver.pop();
			for(BoolExpr axVar: unsatCore) {
				solver.add(track.get(axVar));
			}
			solver.add(ctx.mkNot(execution));
		}
	}

	public static boolean canDrawGraph(AbstractAssert ass, boolean result) {
		String type = ass.getType();
		if(type == null) {
			return result;
		}

		if(result) {
			return type.equals(AbstractAssert.ASSERT_TYPE_EXISTS) || type.equals(AbstractAssert.ASSERT_TYPE_FINAL);
		}
		return type.equals(AbstractAssert.ASSERT_TYPE_NOT_EXISTS) || type.equals(AbstractAssert.ASSERT_TYPE_FORALL);
	}

	public static void drawGraph(Graph graph, String path) throws IOException {
		File newTextFile = new File(path);
		FileWriter fw = new FileWriter(newTextFile);
		fw.write(graph.toString());
		fw.close();
	}
}
