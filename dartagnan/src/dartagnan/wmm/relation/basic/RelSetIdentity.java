package dartagnan.wmm.relation.basic;

import dartagnan.program.event.Event;
import dartagnan.program.event.filter.FilterAbstract;
import dartagnan.wmm.relation.utils.Tuple;
import dartagnan.wmm.relation.utils.TupleSet;

public class RelSetIdentity extends StaticRelation {

    protected FilterAbstract filter;

    public static String makeTerm(FilterAbstract filter){
        return "[" + filter + "]";
    }

    public RelSetIdentity(FilterAbstract filter) {
        this.filter = filter;
        term = makeTerm(filter);
    }

    public RelSetIdentity(FilterAbstract filter, String name) {
        super(name);
        this.filter = filter;
        term = makeTerm(filter);
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Event e1 : program.getEventRepository().getEvents(filter.toRepositoryCode())){
                if(filter.filter(e1)){
                    maxTupleSet.add(new Tuple(e1, e1));
                }
            }
        }
        return maxTupleSet;
    }
}
