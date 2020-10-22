package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.utils.Encoder;
import com.dat3m.dartagnan.utils.EncoderFO;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    @Override
    public TupleSet getEncodeTupleSet(){
        Map<Event, Set<Event>> transMap = rel.getMaxTupleSet().transMap();
        TupleSet result = new TupleSet();

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
        return result;
    }

    @Override
    protected BoolExpr _consistent(Encoder ctx, Settings settings) {
        if(settings.getMode() == Mode.FO) {
            assert ctx instanceof EncoderFO;
            EncoderFO c = (EncoderFO)ctx;
            Expr[] e = new Expr[]{c.bind(0), c.bind(1)};
            BoolExpr e1 = c.edge(rel.getName()).of(e[0], e[1]);
            IntExpr e2 = c.intVar(rel.getName()).of(e[0]);
            return c.mkAnd(
                c.forall(new Expr[]{e[0]}, c.mkLt(c.mkInt(0), e2), c.pattern(e2)),
                c.forall(e, c.mkImplies(e1, c.mkLt(e2, c.intVar(rel.getName()).of(e[1]))), c.pattern(e1)));
        }
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();
            enc = ctx.mkAnd(enc, ctx.mkImplies(e1.exec(), ctx.mkGt(ctx.intVar(rel.getName(), e1), ctx.mkInt(0))));
            enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.edge(rel.getName(), e1, e2), ctx.mkLt(ctx.intVar(rel.getName(), e1), ctx.intVar(rel.getName(), e2))));
        }
        return enc;
    }

    @Override
    protected BoolExpr _inconsistent(Encoder ctx, Settings settings) {
        if(settings.getMode() == Mode.FO) {
            assert ctx instanceof EncoderFO;
            EncoderFO c = (EncoderFO)ctx;
            Expr[] e = new Expr[]{c.bind(0), c.bind(1)};
            BoolExpr c0 = c.cycleVar(rel.getName()).of(e[0]);
            BoolExpr c1 = c.cycleVar(rel.getName()).of(e[1]);
            BoolExpr edge = c.edge(rel.getName()).of(e[0], e[1]);
            BoolExpr cycleEdge = c.cycleEdge(rel.getName()).of(e[0], e[1]);
            return c.mkAnd(
                c.forall(e, c.mkImplies(cycleEdge, c.mkAnd(c0, c1, edge)), c.pattern(edge)),
                c.forall(new Expr[]{e[0]}, c.mkImplies(c0,
                    c.exists(new Expr[]{e[1]}, cycleEdge, c.pattern(edge))), c.pattern(c0)),
                c.forall(new Expr[]{e[1]}, c.mkImplies(c1,
                    c.exists(new Expr[]{e[0]}, cycleEdge, c.pattern(edge))), c.pattern(c1)),
                c.exists(e, cycleEdge, c.pattern(edge)));
        }
        return ctx.mkAnd(satCycleDef(ctx), satCycle(ctx));
    }

    @Override
    protected String _toString() {
        return "acyclic " + rel.getName();
    }

    private BoolExpr satCycle(Encoder ctx) {
        Set<Event> cycleEvents = new HashSet<>();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            cycleEvents.add(tuple.getFirst());
        }

        BoolExpr cycle = ctx.mkFalse();
        for(Event e : cycleEvents){
            cycle = ctx.mkOr(cycle, ctx.cycleVar(rel.getName(), e));
        }

        return cycle;
    }

    private BoolExpr satCycleDef(Encoder ctx){
        BoolExpr enc = ctx.mkTrue();
        Set<Event> encoded = new HashSet<>();
        String name = rel.getName();

        for(Tuple t : rel.getEncodeTupleSet()){
            Event e1 = t.getFirst();
            Event e2 = t.getSecond();

            enc = ctx.mkAnd(enc, ctx.mkImplies(
                    ctx.cycleEdge(name, e1, e2),
                    ctx.mkAnd(
                            e1.exec(),
                            e2.exec(),
                            ctx.edge(name, e1, e2),
                            ctx.cycleVar(name, e1),
                            ctx.cycleVar(name, e2)
            )));

            if(!encoded.contains(e1)){
                encoded.add(e1);

                BoolExpr source = ctx.mkFalse();
                for(Tuple tuple1 : rel.getEncodeTupleSet().getByFirst(e1)){
                    BoolExpr opt = ctx.cycleEdge(name, e1, tuple1.getSecond());
                    for(Tuple tuple2 : rel.getEncodeTupleSet().getByFirst(e1)){
                        if(tuple1.getSecond().getCId() != tuple2.getSecond().getCId()){
                            opt = ctx.mkAnd(opt, ctx.mkNot(ctx.cycleEdge(name, e1, tuple2.getSecond())));
                        }
                    }
                    source = ctx.mkOr(source, opt);
                }

                BoolExpr target = ctx.mkFalse();
                for(Tuple tuple1 : rel.getEncodeTupleSet().getBySecond(e1)){
                    BoolExpr opt = ctx.cycleEdge(name, tuple1.getFirst(), e1);
                    for(Tuple tuple2 : rel.getEncodeTupleSet().getBySecond(e1)){
                        if(tuple1.getFirst().getCId() != tuple2.getFirst().getCId()){
                            opt = ctx.mkAnd(opt, ctx.mkNot(ctx.cycleEdge(name, tuple2.getFirst(), e1)));
                        }
                    }
                    target = ctx.mkOr(target, opt);
                }

                enc = ctx.mkAnd(enc, ctx.mkImplies(ctx.cycleVar(name, e1), ctx.mkAnd(source, target)));
            }
        }

        return enc;
    }
}
