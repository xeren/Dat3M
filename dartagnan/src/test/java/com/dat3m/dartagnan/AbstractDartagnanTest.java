package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.ResourceHelper;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public abstract class AbstractDartagnanTest {

	static Iterable<Object[]> buildParameters(String litmusPath, String cat, Arch target) throws IOException {
		int n = ResourceHelper.LITMUS_RESOURCE_PATH.length();
		Map<String,Result> expectationMap = ResourceHelper.getExpectedResults();
		Wmm wmm = new ParserCat().parse(new File(ResourceHelper.CAT_RESOURCE_PATH + cat));

		Settings s1 = new Settings(Mode.KNASTER, Alias.CFIS, 1);
		Settings s2 = new Settings(Mode.IDL, Alias.CFIS, 1);
		Settings s3 = new Settings(Mode.KLEENE, Alias.CFIS, 1);

		return Files.walk(Paths.get(ResourceHelper.LITMUS_RESOURCE_PATH + litmusPath))
			.filter(Files::isRegularFile)
			.map(Path::toString)
			.filter(f->f.endsWith("litmus"))
			.filter(f->expectationMap.containsKey(f.substring(n)))
			.map(f->new Object[]{f, expectationMap.get(f.substring(n))})
			.collect(ArrayList::new,
				(l, f)->{
					l.add(new Object[]{f[0], f[1], target, wmm, s1});
					l.add(new Object[]{f[0], f[1], target, wmm, s2});
					l.add(new Object[]{f[0], f[1], target, wmm, s3});
				}, ArrayList::addAll);
	}

	private String path;
	private Result expected;
	private Arch target;
	private Wmm wmm;
	private Settings settings;

	AbstractDartagnanTest(String path, Result expected, Arch target, Wmm wmm, Settings settings) {
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
			if(program.getAss() != null) {
				try(EncodeContext context = new EncodeContext(Settings.TACTIC)) {
					assertEquals(expected, Dartagnan.testProgram(context, new ProgramCache(program), wmm, target, settings));
				}
			}
		} catch(IOException e) {
			fail("Missing resource file");
		}
	}
}
