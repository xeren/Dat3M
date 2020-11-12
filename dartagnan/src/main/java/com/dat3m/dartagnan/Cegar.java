package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.utils.options.BaseOptions;
import com.dat3m.dartagnan.wmm.Computation;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelLoc;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelRf;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.alias.AliasAnalysis;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Cegar {

	public static void main(String[] argument) throws IOException, ParseException {
		BaseOptions options = new Options();
		options.parse(argument);
		Settings settings = options.getSettings();
		Wmm model = new ParserCat().parse(new File(options.getTargetModelFilePath()));
		Program program = new ProgramParser().parse(new File(options.getProgramFilePath()));
		boolean specifyForAll = (null != program.getArch() ? program.getAss() : program.createAssertion()).getInvert();
		if(test(model, options.getTarget(), program, settings)) {
			if(specifyForAll) {
				System.out.println("Witnessed specified execution.");
			} else {
				System.out.println("Some feasible execution violates the assertion.");
			}
		} else {
			if(specifyForAll) {
				System.out.println("All feasible executions satisfy the assertion.");
			} else {
				System.out.println("Unable to witness specified execution.");
			}
		}
	}

	/**
	 * @return
	 * There was a feasible computation reaching a queried state.
	 */
	public static boolean test(Wmm model, Arch arch, Program program, Settings settings) {
		program.unroll(settings.getBound(), 0);
		program.compile(arch, 0);

		try(Context c = new Context()) {
			Solver s = c.mkSolver();
			s.add(program.encodeUINonDet(c));
			s.add(program.encodeCF(c));
			s.add(program.encodeFinalRegisterValues(c));

			s.add((null != program.getAss() ? program.getAss() : program.createAssertion()).encode(c));

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

			while(check(s)) {
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

				if(check(sat, special.toArray(new BoolExpr[0]))) {
					return true;
				}

				BoolExpr[] core = sat.getUnsatCore();
				System.err.println("Spurious " + Arrays.toString(core));
				s.add(c.mkNot(c.mkAnd(Arrays.stream(core).toArray(BoolExpr[]::new))));
			}
		}
		return false;
	}

	private static boolean check(Solver s) {
		switch(s.check()) {
			case SATISFIABLE:
				return true;
			case UNSATISFIABLE:
				return false;
			default:
				throw new RuntimeException(s.getReasonUnknown());
		}
	}

	private static boolean check(Solver s, BoolExpr[] query) {
		switch(s.check(query)) {
			case SATISFIABLE:
				return true;
			case UNSATISFIABLE:
				return false;
			default:
				throw new RuntimeException(s.getReasonUnknown());
		}
	}

	private static class Options extends BaseOptions {

		public Options() {
			Option catOption = new Option("cat", true,
				"Path to the CAT file");
			catOption.setRequired(true);
			addOption(catOption);
		}
	}
}
