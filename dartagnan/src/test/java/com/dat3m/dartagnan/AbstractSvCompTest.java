package com.dat3m.dartagnan;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Wmm;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static com.dat3m.dartagnan.analysis.Base.runAnalysis;
import static com.dat3m.dartagnan.analysis.Base.runAnalysisIncrementalSolver;
import static com.dat3m.dartagnan.utils.Result.FAIL;
import static com.dat3m.dartagnan.utils.Result.PASS;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public abstract class AbstractSvCompTest {

	private final String path;
	private final Wmm wmm;
	private final Settings settings;
	private Result expected;

	public AbstractSvCompTest(String path, Wmm wmm, Settings settings) {
		this.path = path;
		this.wmm = wmm;
		this.settings = settings;
	}

	@Test(timeout = 180000)
	public void test() {
		try {
			String property = path.substring(0, path.lastIndexOf("-")) + ".yml";
			expected = readExptected(property);
			Program program = new ProgramParser().parse(new File(path));
			Context ctx = new Context();
			Solver solver = ctx.mkSolver();
			assertEquals(runAnalysis(solver, ctx, program, wmm, settings), expected);
			ctx.close();
		} catch(IOException e) {
			fail("Missing resource file");
		}
	}

	@Test(timeout = 180000)
	public void testIncremental() {
		try {
			String property = path.substring(0, path.lastIndexOf("-")) + ".yml";
			expected = readExptected(property);
			Program program = new ProgramParser().parse(new File(path));
			Context ctx = new Context();
			Solver solver = ctx.mkSolver();
			assertEquals(runAnalysisIncrementalSolver(solver, ctx, program, wmm, settings), expected);
			ctx.close();
		} catch(IOException e) {
			fail("Missing resource file");
		}
	}

	private Result readExptected(String property) {
		try(BufferedReader br = new BufferedReader(new FileReader(new File(property)))) {
			while(!(br.readLine()).contains("unreach-call.prp")) ;
			return br.readLine().contains("false") ? FAIL : PASS;

		} catch(Exception e) {
			System.out.println(e.getMessage());
			System.exit(0);
		}
		return null;
	}
}