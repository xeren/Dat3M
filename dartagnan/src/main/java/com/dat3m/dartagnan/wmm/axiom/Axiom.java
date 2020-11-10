package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.wmm.Computation;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
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

    public BoolExpr encodeRelAndConsistency(Context ctx) {
    	return ctx.mkAnd(rel.encode(), consistent(ctx));
    }
    
    public BoolExpr consistent(Context ctx) {
        if(negate){
            return _inconsistent(ctx);
        }
        return _consistent(ctx);
    }

    public BoolExpr inconsistent(Context ctx) {
        if(negate){
            return _consistent(ctx);
        }
        return _inconsistent(ctx);
    }

    @Override
    public String toString(){
        if(negate){
            return "~" + _toString();
        }
        return _toString();
    }

    public abstract TupleSet getEncodeTupleSet();

    protected abstract BoolExpr _consistent(Context ctx);

    protected abstract BoolExpr _inconsistent(Context ctx);

    public abstract BoolExpr encode(Context context, Computation computation);

    public BoolExpr consistent(Context context, Computation computation) {
        BoolExpr result = _consistent(context, computation);
        return negate ? context.mkNot(result) : result;
    }

    protected abstract BoolExpr _consistent(Context context, Computation computation);

    protected abstract String _toString();
}
