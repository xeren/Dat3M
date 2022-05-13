package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.program.analysis.AliasAnalysis;
import com.dat3m.dartagnan.program.analysis.ExclusiveAccesses;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.Load;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.event.core.rmw.RMWStore;
import com.dat3m.dartagnan.program.event.lang.svcomp.EndAtomic;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.program.filter.FilterIntersection;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.FormulaManager;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.List;
import java.util.stream.Collectors;

import static com.dat3m.dartagnan.encoding.ProgramEncoder.exclusivePairVariable;
import static com.dat3m.dartagnan.expression.utils.Utils.generalEqual;
import static com.dat3m.dartagnan.program.event.Tag.SVCOMP.SVCOMPATOMIC;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.RMW;

/*
    NOTE: Changes to the semantics of this class may need to be reflected
    in RMWGraph for Refinement!
 */
public class RelRMW extends StaticRelation {

	private static final Logger logger = LogManager.getLogger(RelRMW.class);

    public RelRMW(){
        term = RMW;
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

            // RMWLoad -> RMWStore
            FilterAbstract filter = FilterIntersection.get(FilterBasic.get(Tag.RMW), FilterBasic.get(Tag.WRITE));
            for(Event store : task.getProgram().getCache().getEvents(filter)){
            	if(store instanceof RMWStore) {
                    minTupleSet.add(new Tuple(((RMWStore)store).getLoadEvent(), store));
            	}
            }

            //Locks: Load -> CondJump -> Store
            filter = FilterIntersection.get(FilterBasic.get(Tag.RMW), FilterBasic.get(Tag.C11.LOCK));
            for(Event e : task.getProgram().getCache().getEvents(filter)){
            	if(e instanceof Load) {
            	    // Connect Load to Store
                    minTupleSet.add(new Tuple(e, e.getSuccessor().getSuccessor()));
            	}
            }

            // Atomics blocks: BeginAtomic -> EndAtomic
            filter = FilterIntersection.get(FilterBasic.get(Tag.RMW), FilterBasic.get(SVCOMPATOMIC));
            for(Event end : task.getProgram().getCache().getEvents(filter)){
                List<Event> block = ((EndAtomic)end).getBlock().stream().filter(x -> x.is(Tag.VISIBLE)).collect(Collectors.toList());
                for (int i = 0; i < block.size(); i++) {
                    for (int j = i + 1; j < block.size(); j++) {
                        minTupleSet.add(new Tuple(block.get(i), block.get(j)));
                    }

                }
            }
            removeMutuallyExclusiveTuples(minTupleSet);

            maxTupleSet = new TupleSet();
            maxTupleSet.addAll(minTupleSet);

            // LoadExcl -> StoreExcl
            ExclusiveAccesses excl = analysisContext.requires(ExclusiveAccesses.class);
            AliasAnalysis alias = analysisContext.requires(AliasAnalysis.class);
            for(MemEvent store : excl.getStores()) {
                for(ExclusiveAccesses.LoadInfo info : excl.getLoads(store)) {
                    Tuple tuple = new Tuple(info.load,store);
                    maxTupleSet.add(tuple);
                    if(info.intermediates.isEmpty() && alias.mustAlias(info.load,store)) {
                        minTupleSet.add(tuple);
                    }
                }
            }
            logger.info("maxTupleSet size for " + getName() + ": " + maxTupleSet.size());
        }
        return maxTupleSet;
    }

    @Override
    public BooleanFormula encode(SolverContext ctx) {
        FormulaManager fmgr = ctx.getFormulaManager();
		BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();

        BooleanFormula enc = bmgr.makeTrue();

        // Encode RMW for exclusive pairs
        ExclusiveAccesses excl = analysisContext.requires(ExclusiveAccesses.class);
        AliasAnalysis alias = analysisContext.requires(AliasAnalysis.class);
        for(MemEvent store : excl.getStores()) {
            for(ExclusiveAccesses.LoadInfo info : excl.getLoads(store)) {
                // Encode if load and store form an exclusive pair
                BooleanFormula isPair = info.intermediates.isEmpty()
                    ? bmgr.makeTrue()
                    : exclusivePairVariable(info.load,store,ctx);
                // If load and store have the same address
                BooleanFormula sameAddress = alias.mustAlias(info.load,store)
                    ? bmgr.makeTrue()
                    : generalEqual(info.load.getMemAddressExpr(),store.getMemAddressExpr(),ctx);
                // Relation between exclusive load and store
                enc = bmgr.and(enc,bmgr.equivalence(getSMTVar(info.load,store,ctx),bmgr.and(getExecPair(info.load,store,ctx),isPair,sameAddress)));

                // Can be executed if addresses mismatch, but behaviour is "constrained unpredictable"
                // The implementation does not include all possible unpredictable cases: in case of address
                // mismatch, addresses of read and write are unknown, i.e. read and write can use any address
            }
        }
        return enc;
    }
}