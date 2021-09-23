package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Load;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.svcomp.event.EndAtomic;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterIntersection;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Flag;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.FormulaManager;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.dat3m.dartagnan.program.utils.EType.SVCOMPATOMIC;
import static com.dat3m.dartagnan.program.utils.Utils.generalEqual;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.RMW;
import static org.sosy_lab.java_smt.api.FormulaType.BooleanType;

/*
    NOTE: Changes to the semantics of this class may need to be reflected
    in RMWGraph for Refinement!
 */
public class RelRMW extends StaticRelation {

	private static final Logger logger = LogManager.getLogger(RelRMW.class);

    public RelRMW(){
        term = RMW;
        forceDoEncode = true;
    }

    @Override
    public TupleSet getMinTupleSet(){
        if(minTupleSet == null){
            getMaxTupleSet();

        }
        return minTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
        	logger.info("Computing maxTupleSet for " + getName());
			minTupleSet = new TupleSet();

            // Atomics blocks: BeginAtomic -> EndAtomic
			for(Event end : task.getProgram().getCache().getEvents(FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(SVCOMPATOMIC)))){
                List<Event> block = ((EndAtomic)end).getBlock().stream().filter(x -> x.is(EType.VISIBLE)).collect(Collectors.toList());
                for (int i = 0; i < block.size(); i++) {
                    for (int j = i + 1; j < block.size(); j++) {
						minTupleSet.add(new Tuple(block.get(i), block.get(j)));
                    }

                }
            }
			removeMutuallyExclusiveTuples(minTupleSet);

            maxTupleSet = new TupleSet();

			var eq = task.getProgram().getBranchEquivalence();
			for(var w : task.getProgram().getCache().getEvents(FilterIntersection.get(FilterBasic.get(EType.RMW), FilterBasic.get(EType.WRITE)))) {
				var id = w.getCId();
				//RMW events before store
				var candidate = w.getThread().getCache().getEvents(FilterBasic.get(EType.RMW)).stream()
				.filter(l -> l.getCId() < id)
				.collect(Collectors.toUnmodifiableList());
				//assert cId order
				//filter candidates overwritten by implications of w
				var begin = IntStream.range(1, candidate.size() + 1)
				.map(i -> candidate.size() - i)
				.filter(i -> eq.isImplied(w, candidate.get(i)))
				.findFirst().orElse(0);
				//filter candidates overwritten by intermediates
				//TODO some returned loads may be branch-covered and should not appear
				//current implementation ensures singleton except for reserve/exclusive in litmus tests
				var result = IntStream.range(begin, candidate.size())
				.filter(i -> IntStream.range(i+1, candidate.size())
					.noneMatch(j -> eq.isImplied(candidate.get(i), candidate.get(j))))
				.mapToObj(candidate::get)
				//filter loads
				.filter(Load.class::isInstance)
				//filter impossible pairs
				.filter(l -> !eq.areMutuallyExclusive(l, w))
				.toArray(Event[]::new);
				if(result.length == 0)
					throw new IllegalStateException("missing load to rmw store " + w);
				for(int i = 0; i < result.length; ++i) {
					var r = result[i];
					(Arrays.stream(result, i + 1, result.length)
						.allMatch(a -> eq.areMutuallyExclusive(a, r))
						? minTupleSet
						: maxTupleSet)
					.add(new Tuple(r, w));
				}
			}
			maxTupleSet.addAll(minTupleSet);
            logger.info("maxTupleSet size for " + getName() + ": " + maxTupleSet.size());
        }
        return maxTupleSet;
    }

    @Override
    protected BooleanFormula encodeApprox(SolverContext ctx) {
        FormulaManager fmgr = ctx.getFormulaManager();
		BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();
        
        // Encode base (not exclusive pairs) RMW
        TupleSet origEncodeTupleSet = encodeTupleSet;
		encodeTupleSet = new TupleSet(Sets.intersection(encodeTupleSet, minTupleSet));
        BooleanFormula enc = super.encodeApprox(ctx);
        encodeTupleSet = origEncodeTupleSet;

        // Encode RMW for exclusive pairs
		BooleanFormula unpredictable = bmgr.makeFalse();
		var m = new HashMap<Event,BooleanFormula>();
		for(var t : maxTupleSet) {
			var variable = getSMTVar(t, ctx);
			var first = maxTupleSet.getBySecond(t.getSecond());
			assert !first.isEmpty();
			if(first.size() == 1) {
				enc = bmgr.and(enc, bmgr.equivalence(variable, getExecPair(t, ctx)));
				continue;
			}
			var intermediate = bmgr.and(t.getFirst().exec(), t.getSecond().cf());
			for(var u : first) {
				var f = u.getFirst();
				if(t.getFirst().getCId() < f.getCId())
					continue;
				intermediate = bmgr.and(intermediate, bmgr.not(f.is(EType.WRITE) ? f.cf() : f.exec()));
			}
			var isPair = exclPair(t.getFirst(), t.getSecond(), ctx);
			m.compute(t.getSecond(), (k, v) -> null==v ? isPair : bmgr.or(v, isPair));
			enc = bmgr.and(enc, bmgr.equivalence(isPair, intermediate));
			var loc = generalEqual(((MemEvent)t.getFirst()).getMemAddressExpr(), ((MemEvent)t.getSecond()).getMemAddressExpr(), ctx);
			enc = bmgr.and(enc, bmgr.equivalence(variable, bmgr.and(isPair, loc)));
			unpredictable = bmgr.or(unpredictable, bmgr.and(isPair, bmgr.not(loc)));
		}
		for(var e : m.entrySet())
			enc = bmgr.and(enc, bmgr.implication(e.getKey().exec(), e.getValue()));
        return bmgr.and(enc, bmgr.equivalence(Flag.ARM_UNPREDICTABLE_BEHAVIOUR.repr(ctx), unpredictable));
    }

    private BooleanFormula exclPair(Event load, Event store, SolverContext ctx){
    	return ctx.getFormulaManager().makeVariable(BooleanType, "excl(" + load.getCId() + "," + store.getCId() + ")");
    }
}