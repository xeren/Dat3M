package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.CondJump;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import java.util.List;

public class RelCtrlDirect extends StaticRelation {

	public RelCtrlDirect(){
		term = "ctrlDirect";
	}

	@Override
	protected void mkMaxTupleSet(){
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
				addMaxTuple(jump,e,true);
			for(Event e = label; null != e && !(e instanceof CondJump.End); e = e.getSuccessor())
				addMaxTuple(jump,e,true);
		}
    }
}
