package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.utils.Encoder;
import com.dat3m.dartagnan.utils.Settings;
import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

/**
 *
 * @author Florian Furbach
 */
public abstract class Axiom {

    protected Relation rel;

    private boolean negate = false;

    Axiom(Relation rel) {
        this.rel = rel;
    }

    Axiom(Relation rel, boolean negate) {
        this.rel = rel;
        this.negate = negate;
    }

    public Relation getRel() {
        return rel;
    }

    public BoolExpr encodeRelAndConsistency(Encoder ctx, Settings settings) {
    	return ctx.mkAnd(rel.encode(), consistent(ctx, settings));
    }
    
    public BoolExpr consistent(Encoder ctx, Settings settings) {
        if(negate){
            return _inconsistent(ctx, settings);
        }
        return _consistent(ctx, settings);
    }

    public BoolExpr inconsistent(Encoder ctx, Settings settings) {
        if(negate){
            return _consistent(ctx, settings);
        }
        return _inconsistent(ctx, settings);
    }

    @Override
    public String toString(){
        if(negate){
            return "~" + _toString();
        }
        return _toString();
    }

    public abstract TupleSet getEncodeTupleSet();

    protected abstract BoolExpr _consistent(Encoder ctx, Settings settings);

    protected abstract BoolExpr _inconsistent(Encoder ctx, Settings settings);

    protected abstract String _toString();
}
