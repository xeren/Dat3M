package com.dat3m.dartagnan.expression.op;

import com.dat3m.dartagnan.utils.Encoder;
import com.microsoft.z3.BoolExpr;

public enum BOpBin {
    AND, OR;

    @Override
    public String toString() {
        switch(this){
            case AND:
                return "&&";
            case OR:
                return "||";
        }
        return super.toString();
    }

    public BoolExpr encode(BoolExpr e1, BoolExpr e2, Encoder ctx) {
        switch(this) {
            case AND:
                return ctx.mkAnd(e1, e2);
            case OR:
                return ctx.mkOr(e1, e2);
        }
        throw new UnsupportedOperationException("Encoding of not supported for BOpBin " + this);
    }

    public boolean combine(boolean a, boolean b){
        switch(this){
            case AND:
                return a && b;
            case OR:
                return a || b;
        }
        throw new UnsupportedOperationException("Illegal operator " + this + " in BOpBin");
    }
}
