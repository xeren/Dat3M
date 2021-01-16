package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.ResourceHelper;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.Filter;
import com.dat3m.dartagnan.wmm.relation.EdgeTestHelper;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import com.microsoft.z3.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.dat3m.dartagnan.analysis.Base.runAnalysis;
import static com.dat3m.dartagnan.utils.Result.FAIL;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class RelCritTest {

	@Parameterized.Parameters(name = "{index}: {0}")
	public static Iterable<Object[]> data() throws IOException {
		Wmm wmm = new ParserCat().parse(new File(ResourceHelper.CAT_RESOURCE_PATH + "cat/linux-kernel.cat"));
		String path = ResourceHelper.TEST_RESOURCE_PATH + "wmm/relation/basic/crit/";

		List<Object[]> data = new ArrayList<>();
		data.add(new Object[]{path + "C-crit-01.litmus", wmm, new int[]{1, 2, 3, 4}});
		data.add(new Object[]{path + "C-crit-02.litmus", wmm, new int[]{1, 4, 2, 3}});
		data.add(new Object[]{path + "C-crit-03.litmus", wmm, new int[]{2, 3}});
		data.add(new Object[]{path + "C-crit-04.litmus", wmm, new int[]{2, 3}});
		data.add(new Object[]{path + "C-crit-05.litmus", wmm, new int[]{1, 2, 4, 7, 5, 6}});
		data.add(new Object[]{path + "C-crit-06.litmus", wmm, new int[]{1, 10, 2, 5, 3, 4, 6, 9, 7, 8}});
		data.add(new Object[]{path + "C-crit-07.litmus", wmm, new int[]{1, 3}});
		data.add(new Object[]{path + "C-crit-08.litmus", wmm, new int[]{1, 6}});
		data.add(new Object[]{path + "C-crit-09.litmus", wmm, new int[]{1, 7, 8, 10}});
		return data;
	}

	private final String path;
	private final Wmm wmm;
	private final int[] expectedEdges;

	public RelCritTest(String path, Wmm wmm, int[] expectedEdges) {
		this.path = path;
		this.wmm = wmm;
		this.expectedEdges = expectedEdges;
	}

	@Test
	public void test() {
		try {
			// Force encoding all possible "crit" relations
			Settings settings = new Settings(Mode.KNASTER, Alias.CFIS, 1, true, "crit");

			Context ctx = new Context();
			Solver solver = ctx.mkSolver(ctx.mkTactic(Settings.TACTIC));
			Program program = new ProgramParser().parse(new File(path));

			// Sanity check, can be skipped
			assertEquals(runAnalysis(solver, ctx, program, wmm, settings), FAIL);

			// Test edges
			EdgeTestHelper helper = new EdgeTestHelper(
				program,
				wmm.getRelationRepository().getRelation("crit"),
				Filter.of(EType.RCU_LOCK),
				Filter.of(EType.RCU_UNLOCK)
			);
			solver.add(helper.encodeIllegalEdges(expectedEdges, ctx));
			assertSame(Status.UNSATISFIABLE, solver.check());

			ctx.close();

		} catch(IOException e) {
			fail("Missing resource file");
		}
	}
}
