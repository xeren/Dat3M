package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static com.dat3m.dartagnan.utils.Result.FAIL;
import static com.dat3m.dartagnan.utils.Result.PASS;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public abstract class AbstractSvCompTest {

	private String path;
	private Wmm wmm;
	private int bound;
	private Result expected;

	public AbstractSvCompTest(String path, Wmm wmm, int bound) {
		this.path = path;
		this.wmm = wmm;
		this.bound = bound;
	}

	@Test
	public void test() {
		try {
			String property = path.substring(0, path.lastIndexOf(".")) + ".yml";
			expected = readExptected(property);

			Program program = new ProgramParser().parse(new File(path));
			try(EncodeContext context = new EncodeContext()) {
				Settings settings = new Settings(Mode.KNASTER, Alias.CFIS, bound);
				assertEquals(Dartagnan.testProgram(context, new ProgramCache(program), wmm, Arch.NONE, settings), expected);
			}
		} catch(IOException e) {
			fail("Missing resource file");
		}
	}

	private Result readExptected(String property) {
		try(BufferedReader br = new BufferedReader(new FileReader(new File(property)))) {
			while(!(br.readLine()).contains("unreach-call.prp")) {
				continue;
			}
			return br.readLine().contains("false") ? FAIL : PASS;

		} catch(Exception e) {
			System.out.println(e.getMessage());
			System.exit(0);
		}
		return null;
	}
}