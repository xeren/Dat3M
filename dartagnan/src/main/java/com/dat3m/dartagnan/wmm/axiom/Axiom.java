package com.dat3m.dartagnan.wmm.axiom;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.wmm.relation.Relation;

import java.util.Map;

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

	public abstract boolean test(boolean[][] edge);

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

	public abstract void getEncodeTupleSet();

    protected abstract BoolExpr _consistent(Context ctx);

    protected abstract BoolExpr _inconsistent(Context ctx);

    protected abstract String _toString();
}
