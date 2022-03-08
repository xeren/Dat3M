package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis;

import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

import java.util.Map;

public final class RelationAnalysisResult {

    private final Map<Relation, Knowledge> knowledgeMap;
    private final Map<Relation, TupleSet> activeSetsMap;

    public RelationAnalysisResult(Map<Relation, Knowledge> knowledgeMap, Map<Relation, TupleSet> activeSetsMap) {
        this.knowledgeMap = knowledgeMap;
        this.activeSetsMap = activeSetsMap;
    }

    public RelationAnalysisResult(Map<Relation, Knowledge> knowledgeMap) {
        this(knowledgeMap, null);
    }

    public Map<Relation, Knowledge> getKnowledgeMap() { return knowledgeMap; }
    public Map<Relation, TupleSet> getActiveSetsMap() { return activeSetsMap; }
    public boolean hasActiveSets() { return activeSetsMap != null; }
}
