package com.dat3m.dartagnan.wmm.axiom;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
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

    public void encodeRelAndConsistency(EncodeContext context, ProgramCache program) {
        rel.encode(context, program);
        context.rule(consistent(context));
    }
    
    public BoolExpr consistent(EncodeContext context) {
        if(negate){
            return _inconsistent(context);
        }
        return _consistent(context);
    }

    public BoolExpr inconsistent(EncodeContext context) {
        if(negate){
            return _consistent(context);
        }
        return _inconsistent(context);
    }

    @Override
    public String toString(){
        if(negate){
            return "~" + _toString();
        }
        return _toString();
    }

    public abstract TupleSet getEncodeTupleSet(ProgramCache program);

    protected abstract BoolExpr _consistent(EncodeContext context);

    protected abstract BoolExpr _inconsistent(EncodeContext context);

    protected abstract String _toString();
}
