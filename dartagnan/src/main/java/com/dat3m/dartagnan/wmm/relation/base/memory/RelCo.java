package com.dat3m.dartagnan.wmm.relation.base.memory;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.filter.FilterMinus;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.*;
import java.util.List;
import java.util.ListIterator;

import static com.dat3m.dartagnan.utils.Settings.FLAG_CURRENT_MODEL_LOCAL_CO;

public class RelCo extends Relation {

    public RelCo(){
        term = "co";
        forceDoEncode = true;
    }

	public IntExpr intVar(Event event) {
		return ctx.mkIntConst("co "+event.getCId());
	}

    @Override
	protected void mkMaxTupleSet(){
		FilterBasic filterInit = FilterBasic.get(EType.INIT);
		FilterMinus filterWrite = FilterMinus.get(FilterBasic.get(EType.WRITE),filterInit);

		for(Event e1 : program.getCache().getEvents(filterInit))
			for(Event e2 : program.getCache().getEvents(filterWrite))
				if(MemEvent.canAddressTheSameLocation((MemEvent) e1, (MemEvent)e2))
					addMaxTuple(e1,e2);

		boolean lc = settings.getFlag(FLAG_CURRENT_MODEL_LOCAL_CO);

		for(ListIterator<Thread> it = program.getThreads().listIterator(); it.hasNext();){
			List<Event> st = it.next().getCache().getEvents(filterWrite);
			if(st.isEmpty())
				continue;
			//internal relationships
			for(ListIterator<Event> i = st.listIterator(); i.hasNext();){
				Event e1 = i.next();
				for(ListIterator<Event> j = st.listIterator(i.nextIndex()); j.hasNext();){
					Event e2 = j.next();
					if(MemEvent.canAddressTheSameLocation((MemEvent)e1,(MemEvent)e2)){
						addMaxTuple(e1,e2);
						if(!lc)
							addMaxTuple(e2,e1);
					}
				}
			}
			//external relationships
			for(ListIterator<Thread> jt = program.getThreads().listIterator(it.nextIndex()); jt.hasNext();){
				for(Event e1 : jt.next().getCache().getEvents(filterWrite)){
					for(Event e2 : st){
						assert e1.getCId() != e2.getCId();
						if(MemEvent.canAddressTheSameLocation((MemEvent)e1,(MemEvent)e2)){
							addMaxTuple(e1,e2);
							addMaxTuple(e2,e1);
						}
					}
				}
			}
		}
	}

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();

		for(Tuple t: maxTupleSet){
			Event x = t.getFirst();
			assert x.is(EType.WRITE);
			Event y = t.getSecond();
			assert y.is(EType.WRITE) && !y.is(EType.INIT);
			BoolExpr edge = edge(x,y);
			BoolExpr order;
			if(maxTupleSet.contains(new Tuple(y,x))){
				assert!x.is(EType.INIT);
				order = ctx.mkLt(intVar(x),intVar(y));
			}
			else{
				order = ctx.mkTrue();
				if(!x.is(EType.INIT))
					enc = ctx.mkAnd(enc,ctx.mkImplies(edge,ctx.mkLt(intVar(x),intVar(y))));
			}
			enc = ctx.mkAnd(enc,ctx.mkEq(edge,ctx.mkAnd(
				x.exec(),
				y.exec(),
				ctx.mkEq(toInt(((MemEvent)x).getMemAddressExpr()),toInt(((MemEvent)y).getMemAddressExpr())),
				order)));
		}

		enc = ctx.mkAnd(enc,ctx.mkDistinct(program.getCache().getEvents(FilterBasic.get(EType.WRITE)).stream()
			.map(this::intVar).toArray(Expr[]::new)));

        for(Event w :  program.getCache().getEvents(FilterBasic.get(EType.WRITE))){
            MemEvent w1 = (MemEvent)w;
            BoolExpr lastCo = w1.exec();

            for(Tuple t : getMaxTupleSet(w1)){
                MemEvent w2 = (MemEvent)t.getSecond();
				lastCo = ctx.mkAnd(lastCo,ctx.mkNot(edge(w1,w2)));
            }

            BoolExpr lastCoExpr = ctx.mkBoolConst("co_last(" + w1.repr() + ")");
            enc = ctx.mkAnd(enc, ctx.mkEq(lastCoExpr, lastCo));

            for(Address address : w1.getMaxAddressSet()){
				Expr a1 = toInt(w1.getMemAddressExpr());
				Expr a2 = toInt(address.toZ3Int(ctx));
				Expr v1 = toInt(address.getLastMemValueExpr(ctx));
				Expr v2 = toInt(w1.getMemValueExpr());
				enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.mkAnd(lastCoExpr, ctx.mkEq(a1, a2)),ctx.mkEq(v1, v2)));
            }
        }
        return enc;
    }

	private Expr toInt(Expr x){
		return x.isBV() ? ctx.mkBV2Int((BitVecExpr)x,false) : x;
	}
}
