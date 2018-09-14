package dartagnan.wmm.relation.basic;

import dartagnan.program.event.Event;
import dartagnan.program.event.linux.rcu.RCUReadUnlock;
import dartagnan.program.utils.EventRepository;
import dartagnan.wmm.relation.utils.Tuple;
import dartagnan.wmm.relation.utils.TupleSet;

public class RelCrit extends StaticRelation {

    public RelCrit(){
        term = "crit";
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Event unlock : program.getEventRepository().getEvents(EventRepository.EVENT_RCU_UNLOCK)){
                maxTupleSet.add(new Tuple(((RCUReadUnlock)unlock).getLockEvent(), unlock));
            }
        }
        return maxTupleSet;
    }
}
