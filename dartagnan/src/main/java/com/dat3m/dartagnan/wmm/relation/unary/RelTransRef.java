package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 *
 * @author Florian Furbach
 */
public class RelTransRef extends RelTrans {

    public static String makeTerm(Relation r1){
        return r1.getName() + "^*";
    }

    public RelTransRef(Relation r1) {
        super(r1);
        term = makeTerm(r1);
    }

    public RelTransRef(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

    @Override
    public TupleSet getMinTupleSet(){
        if(minTupleSet == null){
            super.getMinTupleSet();
            for(Event e : task.getProgram().getCache().getEvents(FilterBasic.get(Tag.VISIBLE))){
                minTupleSet.add(new Tuple(e, e));
            }
        }
        return minTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            super.getMaxTupleSet();
            for(Event e : task.getProgram().getCache().getEvents(FilterBasic.get(Tag.VISIBLE))){
                maxTupleSet.add(new Tuple(e, e));
            }
        }
        return maxTupleSet;
    }
}