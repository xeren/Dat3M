package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.BaseDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Verify.verify;

public final class Fenced extends BaseDefinition {

    FilterAbstract filter;

    public Fenced(Relation baseRel, FilterAbstract f) {
        super(baseRel);
        filter = f;
    }

    @Override
    public Map<Relation,Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation,Knowledge> know) {
        return Map.of();
    }

    @Override
    public Map<Relation,TupleSet> computeActiveSets(Map<Relation,Knowledge> know) {
        return Map.of();
    }

    @Override
    public String getTerm() {
        return "fencerel("+filter+")";
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation,Knowledge> know) {
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        TupleSet maxTupleSet = new TupleSet();
        TupleSet minTupleSet = new TupleSet();
        FilterBasic memory = FilterBasic.get(Tag.MEMORY);
        for(Thread t : task.getProgram().getThreads()){
            List<Event> fences = t.getCache().getEvents(filter);
            List<Event> memEvents = t.getCache().getEvents(memory);
            for(Event fence : fences) {
                int numEventsBeforeFence = (int) memEvents.stream()
                .mapToInt(Event::getCId).filter(id -> id < fence.getCId())
                .count();
                List<Event> eventsBefore = memEvents.subList(0,numEventsBeforeFence);
                List<Event> eventsAfter = memEvents.subList(numEventsBeforeFence,memEvents.size());
                for(Event e1 : eventsBefore) {
                    boolean isImpliedByE1 = exec.isImplied(e1,fence);
                    for(Event e2 : eventsAfter) {
                        if(exec.areMutuallyExclusive(e1,e2)) {
                            continue;
                        }
                        maxTupleSet.add(new Tuple(e1,e2));
                        if(isImpliedByE1 || exec.isImplied(e2,fence)) {
                            minTupleSet.add(new Tuple(e1,e2));
                        }
                    }
                }
            }
        }
        return new Knowledge(maxTupleSet,minTupleSet);
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation,Knowledge> know, EncodingContext ctx) {
        Knowledge k = know.get(definedRelation);
        BooleanFormulaManager bmgr = ctx.getBmgr();
        List<Event> fences = task.getProgram().getCache().getEvents(filter);
        BooleanFormula result = bmgr.makeTrue();
        for(Tuple t : toBeEncoded) {
            verify(k.isUnknown(t));
            verify(t.isSameThread());
            Event x = t.getFirst();
            Event y = t.getSecond();
            BooleanFormula anyFence = fences.stream()
            .filter(f -> x.getCId() < f.getCId() && f.getCId() < y.getCId())
            .map(Event::exec)
            .reduce(bmgr.makeFalse(),bmgr::or);
            result = bmgr.and(result,
                bmgr.equivalence(
                    getSMTVar(t,k,ctx),
                    bmgr.and(t.getFirst().exec(),t.getSecond().exec(),anyFence)));
        }
        return result;
    }
}
