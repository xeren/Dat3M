package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.microsoft.z3.Context;
import java.util.Collection;

abstract class BasicRegRelation extends StaticRelation {

	abstract Collection<Register> getRegisters(Event regReader);

	abstract Collection<Event> getEvents();

	@Override
	public TupleSet getMinTupleSet(){
		if(minTupleSet == null){
			minTupleSet = new TupleSet();
			var b = task.getProgram().getBranchEquivalence();
			for(var e : getEvents()) {
				for(var r : getRegisters(e)) {
					var d = e.getDependency(r);
					for(int i = 0; i < d.size(); ++i) {
						var w = d.get(i);
						if(d.subList(i+1,d.size()).stream()
							.allMatch(x->b.areMutuallyExclusive(w,x)))
							minTupleSet.add(new Tuple(w,e));
					}
				}
			}
		}
		return minTupleSet;
	}

	@Override
	public TupleSet getMaxTupleSet() {
		if(maxTupleSet == null){
			maxTupleSet = new TupleSet();
			for(var e : getEvents())
				for(var r : getRegisters(e))
					for(var w : e.getDependency(r))
						maxTupleSet.add(new Tuple(w,e));
		}
		return maxTupleSet;
	}

	@Override
	public BoolExpr encodeApprox(Context ctx) {
		BoolExpr enc = ctx.mkTrue();
		for(Event e : getEvents()) {
			for(Register r : getRegisters(e)) {
				var d = e.getDependency(r);
				for(int i = 0; i < d.size(); i++) {
					BoolExpr c = ctx.mkFalse();
					for(int j = i + 1; j < d.size(); j++)
						c = ctx.mkOr(c,d.get(j).exec());
					enc = ctx.mkAnd(enc,ctx.mkEq(getSMTVar(d.get(i),e,ctx),ctx.mkAnd(d.get(i).exec(),ctx.mkNot(c))));
				}
			}
		}
		return enc;
	}
}