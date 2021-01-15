package com.dat3m.dartagnan.analysis;

import static com.dat3m.dartagnan.utils.Result.FAIL;
import static com.dat3m.dartagnan.utils.Result.PASS;
import static com.dat3m.dartagnan.utils.Result.UNKNOWN;
import static com.dat3m.dartagnan.wmm.utils.Utils.edge;
import static com.microsoft.z3.Status.SATISFIABLE;

import com.dat3m.dartagnan.asserts.AssertTrue;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.relation.base.local.RelAddrDirect;
import com.dat3m.dartagnan.wmm.relation.base.local.RelIdd;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelRf;
import com.microsoft.z3.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Base {

	public static Result runAnalysis(Solver s1, Context ctx, Program program, Wmm wmm, Settings settings) {
		program.unroll(settings.getBound(), 0);
		// AssertionInline depends on compiled events (copies)
		// Thus we need to set the assertion after compilation
		program.updateAssertion();
		if(program.getAss() instanceof AssertTrue) {
			return PASS;
		}

		// Using two solvers can be faster than using
		// an incremental solver or check-sat-assuming
		Solver s2 = ctx.mkSolver();

		BoolExpr encodeCF = program.encodeCF(ctx);
		s1.add(encodeCF);
		s2.add(encodeCF);

		BoolExpr encodeFinalRegisterValues = program.encodeFinalRegisterValues(ctx);
		s1.add(encodeFinalRegisterValues);
		s2.add(encodeFinalRegisterValues);

		BoolExpr encodeWmm = wmm.encode(program, ctx, settings);
		s1.add(encodeWmm);
		s2.add(encodeWmm);

		BoolExpr encodeConsistency = wmm.consistent(program, ctx);
		s1.add(encodeConsistency);
		s2.add(encodeConsistency);

		s1.add(program.getAss().encode(ctx));
		if(program.getAssFilter() != null) {
			BoolExpr encodeFilter = program.getAssFilter().encode(ctx);
			s1.add(encodeFilter);
			s2.add(encodeFilter);
		}

		BoolExpr encodeNoBoundEventExec = program.encodeNoBoundEventExec(ctx);

		Result res;
		if(s1.check() == SATISFIABLE) {
			s1.add(encodeNoBoundEventExec);
			res = s1.check() == SATISFIABLE ? FAIL : UNKNOWN;
		} else {
			s2.add(ctx.mkNot(encodeNoBoundEventExec));
			res = s2.check() == SATISFIABLE ? UNKNOWN : PASS;
		}

		if(program.getAss().getInvert()) {
			res = res.invert();
		}
		return res;
	}

	public static Result runAnalysisIncrementalSolver(Solver solver, Context ctx, Program program, Wmm wmm, Settings settings) {
		program.unroll(settings.getBound(), 0);
		// AssertionInline depends on compiled events (copies)
		// Thus we need to update the assertion after compilation
		program.updateAssertion();
		if(program.getAss() instanceof AssertTrue) {
			return PASS;
		}

		solver.add(program.encodeCF(ctx));
		solver.add(program.encodeFinalRegisterValues(ctx));
		solver.add(wmm.encode(program, ctx, settings));
		solver.add(wmm.consistent(program, ctx));
		solver.push();
		solver.add(program.getAss().encode(ctx));
		if(program.getAssFilter() != null) {
			solver.add(program.getAssFilter().encode(ctx));
		}

		Result res;
		if(solver.check() == SATISFIABLE) {
			solver.add(program.encodeNoBoundEventExec(ctx));
			res = solver.check() == SATISFIABLE ? FAIL : UNKNOWN;
		} else {
			solver.pop();
			solver.add(ctx.mkNot(program.encodeNoBoundEventExec(ctx)));
			res = solver.check() == SATISFIABLE ? UNKNOWN : PASS;
		}

		return program.getAss().getInvert() ? res.invert() : res;
	}

	public static Result runRefining(Solver s1, Context ctx, Program program, Wmm wmm, Settings settings) {
		program.unroll(settings.getBound(), 0);
		// AssertionInline depends on compiled events (copies)
		// Thus we need to set the assertion after compilation
		program.updateAssertion();
		if(program.getAss() instanceof AssertTrue) {
			return PASS;
		}
		// Using two solvers is much faster than using
		// an incremental solver or check-sat-assuming
		Solver s2 = ctx.mkSolver();

		BoolExpr encodeCF = program.encodeCF(ctx);
		s1.add(encodeCF);
		s2.add(encodeCF);

		BoolExpr encodeFinalRegisterValues = program.encodeFinalRegisterValues(ctx);
		s1.add(encodeFinalRegisterValues);
		s2.add(encodeFinalRegisterValues);

		BoolExpr encodeWmm = wmm.encode(program, ctx, settings);
		RelRf relRf = new RelRf();
		relRf.initialise(program, ctx, settings);
		relRf.addEncodeTupleSet(relRf.getMaxTupleSet());
		RelIdd relIdd = new RelIdd();
		relIdd.initialise(program, ctx, settings);
		relIdd.addEncodeTupleSet(relIdd.getMaxTupleSet());
		RelAddrDirect relAddr = new RelAddrDirect();
		relAddr.initialise(program, ctx, settings);
		relAddr.addEncodeTupleSet(relAddr.getMaxTupleSet());
		BoolExpr encodeEmptyWmm = ctx.mkAnd(relRf.encode(), relIdd.encode(), relAddr.encode());
		s1.add(encodeEmptyWmm);

		BoolExpr encodeConsistency = wmm.consistent(program, ctx);

		BoolExpr encodeAssertion = program.getAss().encode(ctx);
		s1.add(encodeAssertion);
		if(program.getAssFilter() != null) {
			BoolExpr encodeFilter = program.getAssFilter().encode(ctx);
			s1.add(encodeFilter);
			s2.add(encodeFilter);
		}

		BoolExpr encodeNoBoundEventExec = program.encodeNoBoundEventExec(ctx);

		while(s1.check() == SATISFIABLE) {
			Model m = s1.getModel();

			Map<Boolean, List<Event>> executed = program.getEvents().stream()
				.collect(Collectors.groupingBy(e->m.getConstInterp(e.exec()).isTrue()));
			List<Load> loads = executed.get(true).stream()
				.filter(Load.class::isInstance)
				.map(Load.class::cast)
				.collect(Collectors.toList());
			List<InitOrStore> stores = executed.get(true).stream()
				.filter(InitOrStore.class::isInstance)
				.map(InitOrStore.class::cast)
				.collect(Collectors.toList());
			Stream.concat(loads.stream(), stores.stream()).map(MemEvent::getMemAddressExpr).filter(e->null == m.getConstInterp(e)).forEach(System.err::println);
			Map<Expr, List<MemEvent>> loc = Stream.concat(loads.stream(), stores.stream())
				.collect(Collectors.groupingBy(e->m.getConstInterp(e.getMemAddressExpr())));
			Map<Load, InitOrStore> rf = loads.stream()
				.collect(Collectors.toMap(
					r->r,
					r->stores.stream()
						.filter(w->MemEvent.canAddressTheSameLocation(r, w))
						.filter(w->m.getConstInterp(edge("rf", w, r, ctx)).isTrue())
						.findAny().orElseThrow(AssertionError::new)));

			Solver s3 = ctx.mkSolver();
			s3.add(encodeWmm);
			s3.add(encodeConsistency);

			HashMap<BoolExpr,BoolExpr> track = new HashMap<>();
			for(Event e : executed.get(true)) {
				track.put(e.exec(), e.exec());
			}
			//NOTE sometimes all events are executed
			for(Event e : executed.getOrDefault(false, Collections.emptyList())) {
				BoolExpr notExec = ctx.mkNot(e.exec());
				track.put(notExec, notExec);
			}
			for(List<MemEvent> l : loc.values()) {
				for(int i = 0; i < l.size(); i++) {
					MemEvent st2 = l.get(i);
					for(int j = 0; j < i; j++) {
						MemEvent st1 = l.get(j);
						track.put(edge("loc", st1, st2, ctx), ctx.mkEq(st1.getMemAddressExpr(), st2.getMemAddressExpr()));
						track.put(edge("loc", st2, st1, ctx), ctx.mkEq(st1.getMemAddressExpr(), st2.getMemAddressExpr()));
					}
				}
			}
			for(Load r : loads) {
				BoolExpr literal = edge("rf", rf.get(r), r, ctx);
				track.put(literal, literal);
			}
			track.forEach((k,v)->s3.assertAndTrack(v,k));

			if(SATISFIABLE == s3.check()) {
				s1.add(encodeNoBoundEventExec);
				return s1.check() != SATISFIABLE ? UNKNOWN : program.getAss().getInvert() ? PASS : FAIL;
			}

			BoolExpr[] core = s3.getUnsatCore();
			BoolExpr refinement = ctx.mkNot(ctx.mkAnd(Arrays.stream(s3.getUnsatCore()).map(track::get).toArray(BoolExpr[]::new)));
			System.out.println(Arrays.toString(core));
			s1.add(refinement);
			s2.add(refinement);
		}
		s2.add(ctx.mkNot(encodeNoBoundEventExec));
		return s2.check() == SATISFIABLE ? UNKNOWN : program.getAss().getInvert() ? FAIL : PASS;
	}
}
