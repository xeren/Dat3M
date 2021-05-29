package com.dat3m.dartagnan.program.memory;

import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import com.dat3m.dartagnan.program.memory.utils.IllegalMemoryAccessException;

import java.util.*;

public class Memory {

	private final HashMap<String,Location> arrays = new HashMap<>();

    public BoolExpr encode(Context ctx){
        BoolExpr enc = ctx.mkTrue();
		for(var l : arrays.values()){
			var array = l.getAddress();
            Expr e1 = array.get(0).toZ3Int(ctx);
            for(int i = 1; i < array.size(); i++){
                Expr e2 = array.get(i).toZ3Int(ctx);
                Expr newAddress = e1.isBV() ? ctx.mkBVAdd((BitVecExpr)e1, ctx.mkBV(1, array.get(0).getPrecision())) : ctx.mkAdd((IntExpr)e1, ctx.mkInt(1));
				enc = ctx.mkAnd(enc, ctx.mkEq(e2, newAddress));
                e1 = e2;
            }
        }

        // Following SMACK, only address with constant values can have negative values.
        for(Address add : getAllAddresses()) {
        	if(!add.hasConstantValue()) {
        		enc = ctx.mkAnd(enc, add.toZ3Int(ctx).isBV() ? ctx.mkGt(ctx.mkBV2Int((BitVecExpr) add.toZ3Int(ctx), false), ctx.mkInt(0)) : ctx.mkGt((IntExpr)add.toZ3Int(ctx), ctx.mkInt(0)));
        	}
        }
        
        return ctx.mkAnd(enc, ctx.mkDistinct(getAllAddresses().stream().map(a -> a.toZ3Int(ctx).isBV() ? ctx.mkBV2Int((BitVecExpr) a.toZ3Int(ctx), false) : a.toZ3Int(ctx)).toArray(Expr[]::new)));
    }

	public List<Address> malloc(String name, int size, int precision) {
		if(arrays.containsKey(name) || size <= 0)
			throw new IllegalMemoryAccessException("Illegal malloc for " + name);
		return arrays.compute(name,(k,v)->new Location(name,size,precision)).getAddress();
	}

    public ImmutableSet<Address> getAllAddresses(){
		Set<Address> result = new HashSet<>();
		for(var array : arrays.values())
			result.addAll(array.getAddress());
        return ImmutableSet.copyOf(result);
    }
}