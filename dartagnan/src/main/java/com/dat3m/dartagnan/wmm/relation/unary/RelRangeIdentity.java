package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelRangeIdentity extends UnaryRelation {

    public RelRangeIdentity(Relation r1){
        super(r1);
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitRangeIdentity(this, r1);
    }

    @Override
    public TupleSet getMinTupleSet(){
        if(minTupleSet == null){
            ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
            minTupleSet = new TupleSet();
            r1.getMinTupleSet().stream()
                    .filter(t -> exec.isImplied(t.getSecond(), t.getFirst()))
                    .map(t -> new Tuple(t.getSecond(), t.getSecond()))
                    .forEach(minTupleSet::add);
        }
        return minTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = r1.getMaxTupleSet().mapped(t -> new Tuple(t.getSecond(), t.getSecond()));
        }
        return maxTupleSet;
    }

    @Override
    public String getTerm() {
        return "[range(" + r1.getName() + ")]";
    }
}