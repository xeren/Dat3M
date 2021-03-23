package com.dat3m.dartagnan.wmm.axiom;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.IntExpr;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author Florian Furbach
 */
public class Acyclic extends Axiom {

    public Acyclic(Relation rel) {
        super(rel);
    }

    public Acyclic(Relation rel, boolean negate) {
        super(rel, negate);
    }

    public IntExpr intVar(Event event, Context context) {
        return context.mkIntConst(rel.getName()+" "+event.getCId());
    }

	@Override
	public void getEncodeTupleSet(){
		HashMap<Event,HashSet<Event>> transMap = rel.getMaxTupleSetTransitive();
		HashSet<Tuple> result = new HashSet<>();

        for(Event e1 : transMap.keySet()){
            if(transMap.get(e1).contains(e1)){
                for(Event e2 : transMap.get(e1)){
                    if(e2.getCId() != e1.getCId() && transMap.get(e2).contains(e1)){
                        result.add(new Tuple(e1, e2));
                    }
                }
            }
        }

        for(Tuple tuple : rel.getMaxTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                result.add(tuple);
            }
        }

        result.retainAll(rel.getMaxTupleSet());
		rel.addEncodeTupleSet(result);
	}

    @Override
    protected BoolExpr _consistent(Context ctx) {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();
            enc = ctx.mkAnd(enc, ctx.mkImplies(e1.exec(), ctx.mkGt(intVar(e1, ctx), ctx.mkInt(0))));
            enc = ctx.mkAnd(enc, ctx.mkImplies(rel.edge(tuple), ctx.mkLt(intVar(e1, ctx), intVar(e2, ctx))));
        }
        return enc;
    }

    @Override
    protected BoolExpr _inconsistent(Context ctx) {
        return ctx.mkAnd(satCycleDef(ctx), satCycle(ctx));
    }

    @Override
    protected String _toString() {
        return "acyclic " + rel.getName();
    }

    private BoolExpr satCycle(Context ctx) {
		HashSet<Event> cycleEvents = new HashSet<>();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            cycleEvents.add(tuple.getFirst());
        }

        BoolExpr cycle = ctx.mkFalse();
        for(Event e : cycleEvents){
            cycle = ctx.mkOr(cycle, cycleVar(rel.getName(), e, ctx));
        }

        return cycle;
    }

    private BoolExpr satCycleDef(Context ctx){
        BoolExpr enc = ctx.mkTrue();
		HashSet<Event> encoded = new HashSet<>();
        String name = rel.getName();

		HashMap<Event,HashSet<Event>> byFirst = new HashMap<>();
		HashMap<Event,HashSet<Event>> bySecond = new HashMap<>();
		HashSet<Event> empty = new HashSet<>();
		for(Tuple t : rel.getEncodeTupleSet()){
			Event x = t.getFirst();
			Event y = t.getSecond();
			byFirst.computeIfAbsent(x,k->new HashSet<>()).add(y);
			bySecond.computeIfAbsent(y,k->new HashSet<>()).add(x);
		}

        for(Tuple t : rel.getEncodeTupleSet()){
            Event e1 = t.getFirst();
            Event e2 = t.getSecond();

            enc = ctx.mkAnd(enc, ctx.mkImplies(
                    cycleEdge(name, e1, e2, ctx),
                    ctx.mkAnd(
                            e1.exec(),
                            e2.exec(),
                            rel.edge(t),
                            cycleVar(name, e1, ctx),
                            cycleVar(name, e2, ctx)
            )));

            if(!encoded.contains(e1)){
                encoded.add(e1);

                BoolExpr source = ctx.mkFalse();
				for(Event e3 : byFirst.getOrDefault(e1,empty)){
					BoolExpr opt = cycleEdge(name, e1, e3, ctx);
					for(Event e4 : byFirst.getOrDefault(e1,empty)){
						if(e3.getCId() != e4.getCId()){
							opt = ctx.mkAnd(opt, ctx.mkNot(cycleEdge(name, e1, e4, ctx)));
                        }
                    }
                    source = ctx.mkOr(source, opt);
                }

                BoolExpr target = ctx.mkFalse();
				for(Event e3 : bySecond.getOrDefault(e1,empty)){
					BoolExpr opt = cycleEdge(name, e3, e1, ctx);
					for(Event e4 : bySecond.getOrDefault(e1,empty)){
						if(e3.getCId() != e4.getCId()){
							opt = ctx.mkAnd(opt, ctx.mkNot(cycleEdge(name, e4, e1, ctx)));
                        }
                    }
                    target = ctx.mkOr(target, opt);
                }

                enc = ctx.mkAnd(enc, ctx.mkImplies(cycleVar(name, e1, ctx), ctx.mkAnd(source, target)));
            }
        }

        return enc;
    }

    private BoolExpr cycleVar(String relName, Event e, Context ctx) {
        return ctx.mkBoolConst("Cycle(" + e.repr() + ")(" + relName + ")");
    }

    private BoolExpr cycleEdge(String relName, Event e1, Event e2, Context ctx) {
        return ctx.mkBoolConst("Cycle:" + relName + "(" + e1.repr() + "," + e2.repr() + ")");
    }
}
