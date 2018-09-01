package dartagnan.wmm.relation.basic;

import dartagnan.program.Program;
import dartagnan.program.event.Event;
import dartagnan.program.event.If;
import dartagnan.program.utils.EventRepository;
import dartagnan.wmm.relation.utils.Tuple;

import java.util.HashSet;
import java.util.Set;

public class RelCtrlDirect extends StaticRelation {

    public RelCtrlDirect(){
        term = "ctrlDirect";
    }

    @Override
    public Set<Tuple> getMaxTupleSet(Program program){
        if(maxTupleSet == null){
            maxTupleSet = new HashSet<>();
            for(Event e1 : program.getEventRepository().getEvents(EventRepository.EVENT_IF)){
                for(Event e2 : ((If) e1).getT1().getEvents()){
                    maxTupleSet.add(new Tuple(e1, e2));
                }
                for(Event e2 : ((If) e1).getT2().getEvents()){
                    maxTupleSet.add(new Tuple(e1, e2));
                }
            }
        }
        return maxTupleSet;
    }
}
