package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.Init;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.program.filter.FilterMinus;
import com.dat3m.dartagnan.wmm.analysis.WmmAnalysis;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.BaseDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dat3m.dartagnan.program.event.Tag.*;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.CO;
import static com.google.common.base.Preconditions.checkArgument;

public class MemoryOrder extends BaseDefinition {

    public MemoryOrder(Relation r) {
        super(r);
    }

    @Override
    public String getTerm() {
        return "__" + CO;
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation,Knowledge> know) {
        AliasAnalysis alias = analysisContext.get(AliasAnalysis.class);
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        WmmAnalysis wmm = analysisContext.get(WmmAnalysis.class);
        TupleSet may = new TupleSet();
        TupleSet must = new TupleSet();

        boolean lc = wmm.isLocallyConsistent();
        FilterBasic i = FilterBasic.get(INIT);
        List<Event> storeEvents = task.getProgram().getCache().getEvents(FilterMinus.get(FilterBasic.get(WRITE),i));

        for(Event e1 : storeEvents) {
            for(Event e2 : storeEvents) {
                if(e1==e2
                        || lc && e1.getThread()==e2.getThread() && e1.getCId()>e2.getCId()
                        || exec.areMutuallyExclusive(e1,e2)
                        || !alias.mayAlias((MemEvent)e1,(MemEvent)e2)) {
                    continue;
                }
                may.add(new Tuple(e1,e2));
                if(lc && e1.getThread()==e2.getThread() && e1.getCId()<e2.getCId()
                        && alias.mustAlias((MemEvent)e1,(MemEvent)e2)) {
                    must.add(new Tuple(e1,e2));
                }
            }
        }

        for(Event e1 : task.getProgram().getCache().getEvents(i)) {
            for(Event e2 : storeEvents) {
                if(!alias.mayAlias((MemEvent)e1,(MemEvent)e2)) {
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
    public Map<Relation,Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation,Knowledge> know) {
        assert changed == definedRelation;
        AliasAnalysis alias = analysisContext.get(AliasAnalysis.class);
        TupleSet enable = new TupleSet();

        for(Tuple t : delta.getDisabledSet()) {
            if(alias.mustAlias((MemEvent)t.getFirst(),(MemEvent)t.getSecond())) {
                enable.add(t.getInverse());
            }
        }

        return Map.of(definedRelation,new Knowledge.Delta(new TupleSet(),enable));
    }

    @Override
    public Map<Relation,TupleSet> computeActiveSets(Map<Relation,Knowledge> know) {
        return Map.of();
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation,Knowledge> know, EncodingContext ctx) {
        AliasAnalysis alias = analysisContext.get(AliasAnalysis.class);
        BooleanFormulaManager bmgr = ctx.getBmgr();
        IntegerFormulaManager imgr = ctx.getImgr();
        Knowledge knowledge = know.get(definedRelation);
        NumeralFormula.IntegerFormula zero = imgr.makeNumber(BigInteger.ZERO);

        BooleanFormula enc = bmgr.makeTrue();
        List<NumeralFormula.IntegerFormula> intVars = new ArrayList<>();

        for(Event w : task.getProgram().getCache().getEvents(FilterBasic.get(WRITE))) {
            MemEvent w1 = (MemEvent)w;

            NumeralFormula.IntegerFormula coVar = getIntVar(w,ctx);
            if(w instanceof Init) {
                enc = bmgr.and(enc,imgr.equal(coVar,zero));
            }
            else {
                enc = bmgr.and(enc,imgr.greaterThan(coVar,zero));
                intVars.add(coVar);
            }

            NumeralFormula.IntegerFormula a1 = ctx.convertToIntegerFormula(w1.getMemAddressExpr());
            for(Tuple t : knowledge.getMaySet().getByFirst(w1)) {
                MemEvent w2 = (MemEvent)t.getSecond();
                BooleanFormula order = imgr.lessThan(coVar,getIntVar(w2,ctx));

                if(knowledge.isTrue(t)) {
                    enc = bmgr.and(enc,order);
                    continue;
                }

                BooleanFormula relation = getSMTVar(t,knowledge,ctx);
                BooleanFormula sameAddress = alias.mustAlias(w1,w2)
                        ? bmgr.makeTrue()
                        : imgr.equal(a1,ctx.convertToIntegerFormula(w2.getMemAddressExpr()));
                enc = bmgr.and(enc,bmgr.equivalence(relation,bmgr.and(ctx.execPair(t),sameAddress,order)));
            }
        }

        if(intVars.size() > 1) {
            enc = bmgr.and(enc, imgr.distinct(intVars));
        }

        return enc;
    }

    public NumeralFormula.IntegerFormula getIntVar(Event write, EncodingContext ctx) {
        checkArgument(write.is(WRITE), "Cannot get an int-var for non-writes.");
        return ctx.clockVar(getTerm(),write);
    }
}
