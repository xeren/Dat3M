package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Computation;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelLoc;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelRf;
import com.dat3m.dartagnan.wmm.relation.base.stat.RelPo;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import com.dat3m.dartagnan.wmm.utils.alias.AliasAnalysis;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

public class ModellessTest {

	@Test
	public void test() {
		try {
			Settings settings = new Settings(Mode.KLEENE, Alias.CFS, 1);
			Wmm model = new ParserCat().parse(new File("cat/tso.cat"));
			String path = "litmus/X86/m24.litmus";
			Program program = new ProgramParser().parse(new File(path));
			program.unroll(settings.getBound(), 0);
			program.compile(Arch.TSO, 0);
			try(Context c = new Context()) {
				Solver s = c.mkSolver();
				s.add(program.encodeUINonDet(c));
				s.add(program.encodeCF(c));
				s.add(program.encodeFinalRegisterValues(c));
				new AliasAnalysis().calculateLocationSets(program, settings.getAlias());
				for(Relation r: new Relation[]{new RelRf(), new RelLoc(), new RelPo()}) {
					r.initialise(program, c, settings);
					r.addEncodeTupleSet(r.getMaxTupleSet());
					s.add(r.encode());
				}
				assertEquals(Status.SATISFIABLE, s.check());
				Computation computation = program.extract(c, s.getModel());
				Solver sat = c.mkSolver(c.mkTactic("sat"));
				sat.add(model.consistent(c, computation));
				System.out.println(sat.check());
			}
		} catch(IOException e) {
			fail(e.getMessage());
		}
	}
}
