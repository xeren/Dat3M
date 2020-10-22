package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.utils.Encoder;
import com.dat3m.dartagnan.utils.EncoderFO;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.Expr;

/**
 *
 * @author Florian Furbach
 */
public class Irreflexive extends Axiom {

    public Irreflexive(Relation rel) {
        super(rel);
    }

    public Irreflexive(Relation rel, boolean negate) {
        super(rel, negate);
    }

    @Override
    public TupleSet getEncodeTupleSet(){
        TupleSet set = new TupleSet();
        for(Tuple tuple : rel.getMaxTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                set.add(tuple);
            }
        }
        return set;
    }

    @Override
    protected BoolExpr _consistent(Encoder ctx, Settings settings) {
        if(settings.getMode() == Mode.FO) {
            assert ctx instanceof EncoderFO;
            EncoderFO c = (EncoderFO)ctx;
            Expr[] e = new Expr[]{c.bind(0)};
            BoolExpr e1 = c.edge(rel.getName()).of(e[0], e[0]);
            return c.forall(e, c.mkNot(e1), c.pattern(e1));
        }
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                enc = ctx.mkAnd(enc, ctx.mkNot(ctx.edge(rel.getName(), tuple.getFirst(), tuple.getFirst())));
            }
        }
        return enc;
    }

    @Override
    protected BoolExpr _inconsistent(Encoder ctx, Settings settings) {
        if(settings.getMode() == Mode.FO) {
            assert ctx instanceof EncoderFO;
            EncoderFO c = (EncoderFO)ctx;
            Expr[] e = new Expr[]{c.bind(0)};
            BoolExpr e1 = c.edge(rel.getName()).of(e[0], e[0]);
            return c.exists(e, e1, c.pattern(e1));
        }
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : rel.getEncodeTupleSet()){
            if(tuple.getFirst().getCId() == tuple.getSecond().getCId()){
                enc = ctx.mkOr(enc, ctx.edge(rel.getName(), tuple.getFirst(), tuple.getFirst()));
            }
        }
        return enc;
    }

    @Override
    protected String _toString() {
        return "irreflexive " + rel.getName();
    }
}
