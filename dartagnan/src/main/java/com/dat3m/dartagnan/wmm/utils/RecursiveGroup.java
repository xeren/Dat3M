package com.dat3m.dartagnan.wmm.utils;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.RecursiveRelation;
import com.dat3m.dartagnan.wmm.relation.Relation;

import java.util.*;

public class RecursiveGroup {

    private final int id;
    private List<RecursiveRelation> relations;
    private int encodeIterations = 0;

    public RecursiveGroup(int id, Collection<RecursiveRelation> relations){
        for(RecursiveRelation relation : relations)
            relation.setRecursiveGroupIdR(id);
        this.relations = new ArrayList<>(relations);
        this.id = id;
    }

    public int getId(){
        return id;
    }

    public void encode(EncodeContext context, ProgramCache program){
        for(int i = 0; i < encodeIterations; i++) {
            for(RecursiveRelation relation : relations) {
                relation.encodeIterationR(context, program, id, i);
            }
        }

        for(RecursiveRelation relation : relations)
            relation.encodeFinalIteration(context, encodeIterations - 1);
    }

    public void initMaxTupleSets(ProgramCache program){
        int iterationCounter = 0;
        boolean changed = true;

        while(changed){
            changed = false;
            for(RecursiveRelation relation : relations)
                changed |= relation.getMaxTupleSetRecursiveR(program);
            iterationCounter++;
        }
        // iterationCounter + zero iteration + 1
        encodeIterations = iterationCounter + 2;
    }

    public void updateEncodeTupleSets(ProgramCache program){
        Map<Relation,int[]> encodeSetSizes = new HashMap<>();
        for(Relation relation : relations)
            encodeSetSizes.put(relation, new int[]{0});

        boolean changed = true;
        while(changed){
            changed = false;
            for(RecursiveRelation relation : relations){
                relation.addEncodeTupleSetR(program, relation.getEncodeTupleSet());
                int newSize = relation.getEncodeTupleSet().size();
                int[] value = encodeSetSizes.get(relation);
                changed |= newSize != value[0];
                value[0] = newSize;
            }
        }
    }
}
