package com.dat3m.dartagnan.wmm.relation.base.stat;

import com.dat3m.dartagnan.program.event.CondJump;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelCtrlDirect extends StaticRelation {

	public RelCtrlDirect(){
		term = "ctrlDirect";
	}

	@Override
	public TupleSet getMaxTupleSet(){
		if(maxTupleSet == null){
			maxTupleSet = new TupleSet();
			for(Event jump : program.getCache().getEvents(FilterBasic.get(EType.JUMP))){
				Event label = ((CondJump)jump).getLabel();
				assert null!=label && jump.getCId() < label.getCId();
				for(Event e = jump.getSuccessor(); label != e; e = e.getSuccessor())
					maxTupleSet.add(new Tuple(jump,e));
				for(Event e = label; null != e && !(e instanceof CondJump.End); e = e.getSuccessor())
					maxTupleSet.add(new Tuple(jump,e));
			}
		}
		return maxTupleSet;
	}
}
