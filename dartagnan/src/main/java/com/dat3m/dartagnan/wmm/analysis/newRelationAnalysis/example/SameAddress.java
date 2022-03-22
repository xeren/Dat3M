package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.BaseDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

import java.util.*;

import static com.dat3m.dartagnan.program.event.Tag.*;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.LOC;

public class SameAddress extends BaseDefinition {

    public SameAddress(Relation r) {
        super(r);
    }

    @Override
    public String getTerm() {
        return "__" + LOC;
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        AliasAnalysis alias = analysisContext.get(AliasAnalysis.class);
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        TupleSet may = new TupleSet();
        TupleSet must = new TupleSet();

        List<Event> events = task.getProgram().getCache().getEvents(FilterBasic.get(MEMORY));

        for(Event e1 : events){
            for(Event e2 : events){
                if(exec.areMutuallyExclusive(e1,e2) || !alias.mayAlias((MemEvent)e1,(MemEvent)e2)){
                    continue;
                }
                Tuple t = new Tuple(e1,e2);
                may.add(t);
                if(alias.mustAlias((MemEvent)e1,(MemEvent)e2)) {
                    must.add(t);
                }
            }
        }

        return new Knowledge(may,must);
    }

    @Override
    public Map<Relation,Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        return Map.of();
    }

    @Override
    public Map<Relation,TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        return Map.of();
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation, Knowledge> know, EncodingContext ctx) {
        Knowledge knowledge = know.get(definedRelation);
        BooleanFormulaManager bmgr = ctx.getBmgr();

        BooleanFormula enc = bmgr.makeTrue();

        for(Tuple t : toBeEncoded) {
            enc = bmgr.and(enc,bmgr.equivalence(getSMTVar(t,knowledge,ctx),bmgr.and(
                    ctx.execPair(t),
                    ctx.generalEqual(
                            ((MemEvent)t.getFirst()).getMemAddressExpr(),
                            ((MemEvent)t.getSecond()).getMemAddressExpr()))));
        }

        return enc;
    }
}
