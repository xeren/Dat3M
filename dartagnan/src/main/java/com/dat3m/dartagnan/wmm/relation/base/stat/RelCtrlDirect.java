package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.CondJump;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.If;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import java.util.List;

public class RelCtrlDirect extends StaticRelation {

    public RelCtrlDirect(){
        term = "ctrlDirect";
    }

	@Override
	protected void mkMaxTupleSet(){
		for(Event e1 : program.getCache().getEvents(FilterBasic.get(EType.CMP))){
                    for(Event e2 : ((If) e1).getMainBranchEvents()){
				addMaxTuple(e1,e2,true);
                    }
                    for(Event e2 : ((If) e1).getElseBranchEvents()){
				addMaxTuple(e1,e2,true);
                    }
		}

                List<Event> condJumps = thread.getCache().getEvents(FilterBasic.get(EType.JUMP));
                if(!condJumps.isEmpty()){
                    for(Event e2 : thread.getCache().getEvents(FilterBasic.get(EType.ANY))){
                        for(Event e1 : condJumps){
                            if(e1.getCId() < e2.getCId()){
						addMaxTuple(e1,e2,true);
                            }
                        }
                    }
                }
		for(Event jump : program.getCache().getEvents(FilterBasic.get(EType.JUMP))){
			Event label = ((CondJump)jump).getLabel();
			assert null!=label && jump.getCId() < label.getCId();
			for(Event e = jump.getSuccessor(); label != e; e = e.getSuccessor())
				addMaxTuple(jump,e);
			for(Event e = label; null != e && !(e instanceof CondJump.End); e = e.getSuccessor())
				addMaxTuple(jump,e);
		}
    }
}
