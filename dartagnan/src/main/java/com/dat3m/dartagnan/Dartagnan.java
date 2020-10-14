package com.dat3m.dartagnan;

import static com.dat3m.dartagnan.utils.Result.FAIL;
import static com.dat3m.dartagnan.utils.Result.PASS;
import static com.dat3m.dartagnan.utils.Result.BFAIL;
import static com.dat3m.dartagnan.utils.Result.BPASS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.microsoft.z3.*;
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
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.utils.Arch;
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

		Settings settings = options.getSettings();
		try(EncodeContext context = new EncodeContext())
		{

			ProgramCache cache = new ProgramCache(p);
			Result result = cegar != null
				? runCegar(context, cache, mcm, target, cegar, settings)
				: testProgram(context, cache, mcm, target, settings);

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
				context.context.setPrintMode(Z3_ast_print_mode.Z3_PRINT_SMTLIB_FULL);
				drawGraph(new Graph(context, context.model().orElseThrow(), p, settings.getGraphRelations()), options.getGraphFilePath());
				System.out.println("Execution graph is written to " + options.getGraphFilePath());
			}
		}
	}

	public static Result testProgram(EncodeContext context, ProgramCache cache, Wmm wmm, Arch target, Settings settings) {
		Program program = cache.program;
		program.unroll(settings.getBound(), 0);
		program.compile(target, 0);

		// AssertionInline depends on compiled events (copies)
		// Thus we need to set the assertion after compilation
		if(program.getAss() == null) {
			AbstractAssert ass = program.createAssertion();
			program.setAss(ass);
			// Due to optimizations, the program might be trivially true
			// Not returning here might loop forever for cyclic programs
			if(ass instanceof AssertTrue)
				return PASS;
		}

		program.encodeUINonDet(context);
		program.encodeCF(context);
		program.encodeFinalRegisterValues(context);
		wmm.encode(context, cache, settings);
		context.rule(wmm.consistent(context));

		if(program.getAssFilter() != null)
			context.rule(program.getAssFilter().encode(context));

		Result res;
		context.push();
		context.rule(program.getAss().encode(context));
		if(context.check()) {
			program.encodeNoBoundEventExec(context);
			res = context.check() ? FAIL : BFAIL;
		} else {
			context.pop();
			program.encodeSomeBoundEventExec(context);
			res = context.check() ? BPASS : PASS;
		}

		if(program.getAss().getInvert())
			res = res.invert();
		return res;
	}

	public static Result runCegar(EncodeContext context, ProgramCache cache, Wmm wmm, Arch target, int cegar, Settings settings) {
		Program program = cache.program;
		program.unroll(settings.getBound(), 0);
		program.compile(target, 0);

		// AssertionInline depends on compiled events (copies)
		// Thus we need to set the assertion after compilation
		if(program.getAss() == null) {
			AbstractAssert ass = program.createAssertion();
			program.setAss(ass);
			// Due to optimizations, the program might be trivially true
			// Not returning here might loop forever for cyclic programs
			if(ass instanceof AssertTrue)
				return PASS;
		}

		program.encodeUINonDet(context);
		program.encodeCF(context);
		program.encodeFinalRegisterValues(context);
		wmm.encodeBase(context, cache, settings);
		wmm.getAxioms().get(cegar).encodeRelAndConsistency(context, cache, settings.getMode());

		if(program.getAssFilter() != null)
			context.rule(program.getAssFilter().encode(context));

		// Termination guaranteed because we add a new constraint in each 
		// iteration and thus the formula will eventually become UNSAT
		while(true) {
			// This needs to be pop for the else branch below
			// If not the formula will always remain UNSAT
			context.push();
			context.rule(program.getAss().encode(context));
			if(context.check()) {
				program.encodeNoBoundEventExec(context);
				if(program.getAss().getInvert())
					return context.check() ? PASS : BPASS;
			} else {
				context.pop();
				context.push();
				program.encodeSomeBoundEventExec(context);
				if(!program.getAss().getInvert())
					return context.check() ? BPASS : PASS;
			}
			context.pop();

			context.push();
			context.rule(program.getAss().encode(context));
			Model model = context.model().orElseThrow();
			List<Event> write = program.getCache().getEvents(FilterBasic.get(EType.WRITE));
			BoolExpr execution = context.and(program.getCache().getEvents(FilterBasic.get(EType.READ)).stream()
				.flatMap(r->write.stream()
					.map(w->context.edge("rf", w, r)))
				.filter(e->model.getConstInterp(e) != null && model.getConstInterp(e).isTrue()));
			context.rule(execution);
			wmm.encodeBase(context, cache, settings);
			for(Axiom ax: wmm.getAxioms())
				ax.encodeRelAndConsistency(context, cache, settings.getMode());
			Optional<BoolExpr[]> unsatCore = context.unsatisfiableCore();
			if(unsatCore.isEmpty()) {
				// For CEGAR, the same code above seems to never give BFAIL
				// Thus we add the constraint here to avoid FAIL when the unrolling was not enough
				program.encodeNoBoundEventExec(context);
				return context.check() ? FAIL : BFAIL;
			}
			context.pop();

			for(BoolExpr axVar: unsatCore.get())
				context.rule(axVar);
			context.rule(context.not(execution));
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
		try(FileWriter fw = new FileWriter(path)) {
			fw.write(graph.toString());
		}
	}
}
