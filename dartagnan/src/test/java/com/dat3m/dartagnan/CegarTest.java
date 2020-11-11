package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.ResourceHelper;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class CegarTest {

	private final String path;
	private final Result expected;
	private final Arch target;
	private final Wmm wmm;
	private final Settings settings;

	public CegarTest(String path, Result expected, Arch target, Wmm wmm, Settings settings) {
		this.path = path;
		this.expected = expected;
		this.target = target;
		this.wmm = wmm;
		this.settings = settings;
	}

	@Test
	public void test() {
		try {
			Program program = new ProgramParser().parse(new File(path));
			boolean sat = Cegar.test(wmm, target, program, settings);
			System.err.println(program.getEvents().stream().filter(e->e.is("RMW")).count());
			assertEquals(expected, sat == program.getAss().getInvert() ? Result.FAIL : Result.PASS);
		} catch(IOException e) {
			fail();
		}
	}

	@Parameterized.Parameters(name = "{index}: {0} {4}")
	public static Iterable<Object[]> data() throws IOException {
		int n = ResourceHelper.LITMUS_RESOURCE_PATH.length();
		Map<String,Result> expectationMap = ResourceHelper.getExpectedResults();
		Wmm wmm1 = new ParserCat().parse(new File(ResourceHelper.CAT_RESOURCE_PATH + "cat/tso.cat"));

		Settings s1 = new Settings(Mode.KNASTER, Alias.CFIS, 1);
		//Settings s2 = new Settings(Mode.IDL, Alias.CFIS, 1);
		//Settings s3 = new Settings(Mode.KLEENE, Alias.CFIS, 1);

		return Files.walk(Paths.get(ResourceHelper.LITMUS_RESOURCE_PATH + "litmus/X86/"))
			.filter(Files::isRegularFile)
			.map(Path::toString)
			.filter(f -> f.endsWith("litmus"))
			.filter(f -> expectationMap.containsKey(f.substring(n)))
			.map(f -> new Object[]{f, expectationMap.get(f.substring(n))})
			.collect(ArrayList::new,
				(l, f) -> {
					l.add(new Object[]{f[0], f[1], Arch.TSO, wmm1, s1});
					//l.add(new Object[]{f[0], f[1], Arch.TSO, wmm1, s2});
					//l.add(new Object[]{f[0], f[1], Arch.TSO, wmm1, s3});
				}, ArrayList::addAll);
	}
}
