package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.Computation;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

public class ModellessTest {

	@Test
	public void test() {
		try {
			String path = "litmus/X86/m24.litmus";
			Program program = new ProgramParser().parse(new File(path));
			program.unroll(1, 0);
			program.compile(Arch.TSO, 0);
			try(Context c = new Context()) {
				Solver s = c.mkSolver();
				s.add(program.encodeUINonDet(c));
				s.add(program.encodeCF(c));
				s.add(program.encodeFinalRegisterValues(c));
				//TODO prohibit thin-air reads
				assertEquals(Status.SATISFIABLE, s.check());
				Computation computation = program.extract(c, s.getModel());
				new ParserCat().parse(new File("cat/tso.cat")).encode(computation);
			}
		} catch(IOException e) {
			fail(e.getMessage());
		}
	}
}
