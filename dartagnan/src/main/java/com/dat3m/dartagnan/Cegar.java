package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Computation;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelLoc;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelRf;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import com.dat3m.dartagnan.wmm.utils.alias.AliasAnalysis;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Cegar {

	public static void main(String[] argument) throws IOException {
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

			List<Event> stores = program.getCache().getEvents(FilterBasic.get(EType.WRITE));
			List<Event> loads = program.getCache().getEvents(FilterBasic.get(EType.READ));

			// loc & W*W
			for(int i = 0; i < stores.size(); ++i) {
				MemEvent st1 = (MemEvent)stores.get(i);
				int w = st1.getCId();

				for(int j = 0; j < i; ++j) {
					MemEvent st2 = (MemEvent)stores.get(j);
					if(MemEvent.canAddressTheSameLocation(st1, st2)) {
						s.add(c.mkEq(RelLoc.of(c, w, st2.getCId()), c.mkAnd(st1.exec(), st2.exec(),
							c.mkEq(st1.getMemAddressExpr(), st2.getMemAddressExpr()))));
					}
				}
			}

			for(int i = 0; i < loads.size(); ++i) {
				MemEvent ld = (MemEvent)loads.get(i);
				int r = ld.getCId();

				// loc & R*R
				for(int j = 0; j < i; ++j) {
					MemEvent o = (MemEvent)loads.get(j);
					if(MemEvent.canAddressTheSameLocation(ld, o)) {
						s.add(c.mkEq(RelLoc.of(c, r, o.getCId()), c.mkAnd(ld.exec(), o.exec(),
							c.mkEq(ld.getMemAddressExpr(), o.getMemAddressExpr()))));
					}
				}

				// (loc & R*W) | (loc & W*R) | rf
				BoolExpr prev = c.mkFalse();
				for(Event store: stores) {
					MemEvent st = (MemEvent)store;
					if(MemEvent.canAddressTheSameLocation(ld, st)) {
						int w = st.getCId();
						BoolExpr curr = c.mkBoolConst("rf-until " + w + " " + r);
						BoolExpr rf = RelRf.of(c, w, r);
						BoolExpr loc = RelLoc.of(c, w, r);
						s.add(c.mkEq(curr, c.mkOr(prev, rf)));
						s.add(c.mkNot(c.mkAnd(prev, rf)));
						s.add(c.mkImplies(rf, c.mkAnd(loc, c.mkEq(ld.getMemValueExpr(), st.getMemValueExpr()))));
						s.add(c.mkEq(loc, c.mkAnd(ld.exec(), st.exec(), c.mkEq(ld.getMemAddressExpr(), st.getMemAddressExpr()))));
						prev = curr;
					}
				}
				s.add(prev);
			}

			while(Status.SATISFIABLE == s.check()) {
				Computation computation = program.extract(c, s.getModel());

				Solver sat = c.mkSolver(c.mkTactic("sat"));
				sat.add(model.consistent(c, computation));

				ArrayList<BoolExpr> special = new ArrayList<>();
				computation.forEachLocation((x,y)->special.add(RelLoc.of(c,x.id,y.id)));
				computation.forEachRead(r->{
					special.add(RelRf.of(c, r.from.id, r.id));
					r.from.location.forEach(w->special.add(RelLoc.of(c,r.id,w.id)));
					computation.forEachRead(o->{
						if(r.from.location == o.from.location && r != o)
							special.add(RelLoc.of(c, r.id, o.id));
					});
				});

				if(Status.SATISFIABLE == sat.check(special.toArray(new BoolExpr[0]))) {
					System.out.println("Some execution consistent with the model violates the assertion.");
					return;
				}

				System.out.println("Spurious executions with " + Arrays.toString(sat.getUnsatCore()) + ".");
				s.add(c.mkNot(c.mkAnd(Arrays.stream(sat.getUnsatCore()).toArray(BoolExpr[]::new))));
			}
			System.out.println("All executions consistent with the model satisfy the assertion.");
		}
	}
}
