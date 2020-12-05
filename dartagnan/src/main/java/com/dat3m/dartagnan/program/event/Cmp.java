package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.IExpr;

public class Cmp {

    private final IExpr left;
    private final IExpr right;

    public Cmp(IExpr left, IExpr right){
        this.left = left;
        this.right = right;
    }

    public IExpr getLeft(){
        return left;
    }

    public IExpr getRight(){
        return right;
    }

    @Override
    public String toString(){
        return "cmp(" + left + "," + right + ")";
    }
}
