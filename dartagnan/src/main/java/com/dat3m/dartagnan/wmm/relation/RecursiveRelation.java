package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.utils.Settings;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 *
 * @author Florian Furbach
 */
public class RecursiveRelation extends Relation {

    private Relation r1;
    private boolean doRecurse = false;

    public RecursiveRelation(String name) {
        super(name);
        term = name;
    }

    public static String makeTerm(String name){
        return name;
    }

    public void initialise(Program program, Context ctx, Settings settings){
        if(doRecurse){
            doRecurse = false;
            super.initialise(program, ctx, settings);
            r1.initialise(program, ctx, settings);
        }
    }

    public void setConcreteRelation(Relation r1){
        r1.isRecursive = true;
        r1.setName(name);
        this.r1 = r1;
        this.isRecursive = true;
        this.term = r1.getTerm();
    }

    public void setDoRecurse(){
        doRecurse = true;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
        }
        return maxTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSetRecursive(){
        if(doRecurse){
            doRecurse = false;
            maxTupleSet = r1.getMaxTupleSetRecursive();
            return maxTupleSet;
        }
        return getMaxTupleSet();
    }

    @Override
    public void addEncodeTupleSet(TupleSet tuples){
        if(encodeTupleSet != tuples){
            encodeTupleSet.addAll(tuples);
        }
        if(doRecurse){
            doRecurse = false;
            r1.addEncodeTupleSet(encodeTupleSet);
        }
    }

    @Override
    public void setRecursiveGroupId(int id){
        if(doRecurse){
            doRecurse = false;
            forceUpdateRecursiveGroupId = true;
            recursiveGroupId = id;
            r1.setRecursiveGroupId(id);
        }
    }

    @Override
    public int updateRecursiveGroupId(int parentId){
        if(forceUpdateRecursiveGroupId){
            forceUpdateRecursiveGroupId = false;
            int r1Id = r1.updateRecursiveGroupId(parentId | recursiveGroupId);
            recursiveGroupId |= r1Id & parentId;
        }
        return recursiveGroupId;
    }

    @Override
    protected void doEncode(EncodeContext context) {
        r1.encode(context);
    }

    @Override
    protected void encodeLFP(EncodeContext context) {
        r1.encodeLFP(context);
    }

    @Override
    protected void encodeIDL(EncodeContext context) {
        r1.encodeIDL(context);
    }

    @Override
    protected void encodeApprox(EncodeContext context) {
        r1.encodeApprox(context);
    }

    @Override
    public void encodeIteration(EncodeContext e, int recGroupId, int iteration){
        if(doRecurse){
            doRecurse = false;
            r1.encodeIteration(e, recGroupId, iteration);
        }
    }

    public void encodeFinalIteration(EncodeContext e, int iteration){
        for(Tuple tuple : encodeTupleSet){
            e.rule(e.eq(e.edge(this, tuple), e.edge(this, iteration, tuple)));
        }
    }

    @Override
    protected void encodeFirstOrder(EncodeContext context) {
        r1.encodeFirstOrder(context);
    }
}
