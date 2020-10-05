package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.ResourceHelper;
import com.dat3m.dartagnan.wmm.ProgramCache;
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
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static com.dat3m.dartagnan.utils.Result.FAIL;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class DartagnanArrayValidTest {

	@Parameterized.Parameters(name = "{index}: {0}")
	public static Iterable<Object[]> data() throws IOException {
		Wmm wmm = new ParserCat().parse(new File(ResourceHelper.CAT_RESOURCE_PATH + "cat/linux-kernel.cat"));
		Settings settings = new Settings(Mode.KNASTER, Alias.CFIS, 1);
		return Files.walk(Paths.get(ResourceHelper.TEST_RESOURCE_PATH + "arrays/ok/"))
			.filter(Files::isRegularFile)
			.filter(f->(f.toString().endsWith("litmus")))
			.map(f->new Object[]{f.toString(), wmm, settings})
			.collect(Collectors.toList());
	}

	private String path;
	private Wmm wmm;
	private Settings settings;

	public DartagnanArrayValidTest(String path, Wmm wmm, Settings settings) {
		this.path = path;
		this.wmm = wmm;
		this.settings = settings;
	}

	@Test
	public void test() {
		try {
			Program program = new ProgramParser().parse(new File(path));
			try(EncodeContext context = new EncodeContext(Settings.TACTIC)) {
				assertEquals(FAIL, Dartagnan.testProgram(context, new ProgramCache(program), wmm, Arch.NONE, settings));
			}
		} catch(IOException e) {
			fail("Missing resource file");
		}
	}
}
