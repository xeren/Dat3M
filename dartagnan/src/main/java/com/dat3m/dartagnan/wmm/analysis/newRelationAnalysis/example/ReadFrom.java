package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Axiom;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.BaseDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.relation.RelationNameRepository;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.NumeralFormula;

import java.util.*;

import static com.dat3m.dartagnan.program.event.Tag.READ;
import static com.dat3m.dartagnan.program.event.Tag.WRITE;

public class ReadFrom extends BaseDefinition implements Axiom {

    public ReadFrom(Relation baseRel) {
        super(baseRel);
    }

    @Override
    public String getTerm() {
        return "__" + RelationNameRepository.RF;
    }

    @Override
    public Map<Relation,Knowledge.Delta> computeIncrementalKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation, Knowledge> know) {
        return Map.of();
        // One could check if there is only a single possible rf-edge remaining and set it to T.
        // Or if some rf-edge is T, then one can disable other read froms
    }

    @Override
    public Map<Relation,TupleSet> computeActiveSets(Map<Relation, Knowledge> know) {
        return Map.of(definedRelation,know.get(definedRelation).getMaySet());
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation, Knowledge> know) {
        AliasAnalysis alias = analysisContext.get(AliasAnalysis.class);
        TupleSet maxTupleSet = new TupleSet();
        TupleSet minTupleSet = new TupleSet();

        List<Event> loadEvents = task.getProgram().getCache().getEvents(FilterBasic.get(READ));
        List<Event> storeEvents = task.getProgram().getCache().getEvents(FilterBasic.get(WRITE));

        for(Event e1 : storeEvents){
            for(Event e2 : loadEvents){
                if(alias.mayAlias((MemEvent) e1, (MemEvent) e2)){
                    maxTupleSet.add(new Tuple(e1, e2));
                }
            }
        }

        return new Knowledge(maxTupleSet, minTupleSet);
    }

    @Override
    public Map<Relation,Knowledge.Delta> computeInitialKnowledgeClosure(Map<Relation, Knowledge> know) {
        return Map.of(); // Has been computed already
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation, Knowledge> know, EncodingContext ctx) {
        // ReadFrom has a definitional aspect and a constraining/axiomatic aspect (functionality of rf)
        // The latter cannot be encoded on just a subset. Hence, we do ALL encoding in <encodeAxiom>
        return ctx.getBmgr().makeTrue();
    }

    @Override
    public BooleanFormula encodeAxiom(Map<Relation, Knowledge> know, EncodingContext ctx) {
        Knowledge kRel = know.get(definedRelation);
        BooleanFormulaManager bmgr = ctx.getBmgr();
        IntegerFormulaManager imgr = ctx.getImgr();

        BooleanFormula enc = bmgr.makeTrue();
        Map<MemEvent, List<BooleanFormula>> edgeMap = new HashMap<>();

        for(Tuple tuple : kRel.getMaySet()){
            MemEvent w = (MemEvent) tuple.getFirst();
            MemEvent r = (MemEvent) tuple.getSecond();
            BooleanFormula edge = getSMTVar(tuple, kRel,  ctx);

            NumeralFormula.IntegerFormula a1 = ctx.convertToIntegerFormula(w.getMemAddressExpr());
            NumeralFormula.IntegerFormula a2 = ctx.convertToIntegerFormula(r.getMemAddressExpr());
            BooleanFormula sameAddress = imgr.equal(a1, a2);

            NumeralFormula.IntegerFormula v1 = ctx.convertToIntegerFormula(w.getMemValueExpr());
            NumeralFormula.IntegerFormula v2 = ctx.convertToIntegerFormula(r.getMemValueExpr());
            BooleanFormula sameValue = imgr.equal(v1, v2);

            edgeMap.computeIfAbsent(r, key -> new ArrayList<>()).add(edge);
            enc = bmgr.and(enc, bmgr.implication(edge, bmgr.and(ctx.execPair(w, r), sameAddress, sameValue)));
        }

        for(MemEvent r : edgeMap.keySet()){
            enc = bmgr.and(enc, encodeEdgeSeq(r, edgeMap.get(r), ctx));
        }
        return enc;

    }

    private BooleanFormula encodeEdgeSeq(Event read, List<BooleanFormula> edges, EncodingContext ctx){
        BooleanFormulaManager bmgr = ctx.getBmgr();

        int num = edges.size();
        int readId = read.getCId();
        BooleanFormula lastSeqVar = mkSeqVar(readId, 0, ctx);
        BooleanFormula newSeqVar = lastSeqVar;
        BooleanFormula atMostOne = bmgr.equivalence(lastSeqVar, edges.get(0));

        for(int i = 1; i < num; i++){
            newSeqVar = mkSeqVar(readId, i, ctx);
            atMostOne = bmgr.and(atMostOne, bmgr.equivalence(newSeqVar, bmgr.or(lastSeqVar, edges.get(i))));
            atMostOne = bmgr.and(atMostOne, bmgr.not(bmgr.and(edges.get(i), lastSeqVar)));
            lastSeqVar = newSeqVar;
        }
        BooleanFormula atLeastOne = bmgr.or(newSeqVar, edges.get(edges.size() - 1));

        atLeastOne = bmgr.implication(read.exec(), atLeastOne);
        return bmgr.and(atMostOne, atLeastOne);
    }

    private BooleanFormula mkSeqVar(int readId, int i, EncodingContext ctx) {
        return ctx.getBmgr().makeVariable("s(" + definedRelation.getName() + ",E" + readId + "," + i + ")");
    }
}
