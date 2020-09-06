package com.dat3m.dartagnan.wmm.utils;

import com.dat3m.dartagnan.wmm.relation.EncodeContext;
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

    public void encode(EncodeContext context){
        for(int i = 0; i < encodeIterations; i++) {
            for(RecursiveRelation relation : relations) {
                relation.encodeIterationR(context, id, i);
            }
        }

        for(RecursiveRelation relation : relations)
            relation.encodeFinalIteration(context, encodeIterations - 1);
    }

    public void initMaxTupleSets(EncodeContext context){
        int iterationCounter = 0;
        boolean changed = true;

        while(changed){
            changed = false;
            for(RecursiveRelation relation : relations)
                changed |= relation.getMaxTupleSetRecursiveR(context);
            iterationCounter++;
        }
        // iterationCounter + zero iteration + 1
        encodeIterations = iterationCounter + 2;
    }

    public void updateEncodeTupleSets(EncodeContext context){
        Map<Relation,int[]> encodeSetSizes = new HashMap<>();
        for(Relation relation : relations)
            encodeSetSizes.put(relation, new int[]{0});

        boolean changed = true;
        while(changed){
            changed = false;
            for(RecursiveRelation relation : relations){
                relation.addEncodeTupleSetR(context, relation.getEncodeTupleSet());
                int newSize = relation.getEncodeTupleSet().size();
                int[] value = encodeSetSizes.get(relation);
                changed |= newSize != value[0];
                value[0] = newSize;
            }
        }
    }
}
