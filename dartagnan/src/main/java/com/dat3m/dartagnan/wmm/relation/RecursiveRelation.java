package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.utils.Settings;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import java.util.Collection;
import java.util.Map;

/**
 *
 * @author Florian Furbach
 */
public class RecursiveRelation extends Relation {

    private Relation r1;
    private boolean doRecurse = false;

    public RecursiveRelation(String name) {
        super(name);
        term = name;
    }

    public static String makeTerm(String name){
        return name;
    }

    public void initialise(Program program, Context ctx, Settings settings){
        if(doRecurse){
            doRecurse = false;
            super.initialise(program, ctx, settings);
            r1.initialise(program, ctx, settings);
        }
    }

    public void setConcreteRelation(Relation r1){
        r1.isRecursive = true;
        r1.setName(name);
        this.r1 = r1;
        this.isRecursive = true;
        this.term = r1.getTerm();
    }

    public void setDoRecurse(){
        doRecurse = true;
    }

	@Override
	protected void mkMaxTupleSet(){
	}

	@Override
	public void getMaxTupleSetRecursive(){
		if(doRecurse){
			doRecurse = false;
			r1.getMaxTupleSetRecursive();
			maxTupleSet = r1.getMaxTupleSet();
		}
	}

	@Override
	public void addEncodeTupleSet(Collection<Tuple> tuples){
        if(encodeTupleSet != tuples){
            encodeTupleSet.addAll(tuples);
        }
        if(doRecurse){
            doRecurse = false;
            r1.addEncodeTupleSet(encodeTupleSet);
        }
    }

	@Override
	public boolean[][] test(Map<Relation,boolean[][]> b, int n){
		boolean[][] r = b.computeIfAbsent(this,k->new boolean[n][n]);
		boolean[][] c = b.get(r1);
		//stops recursion
		if(c==null)
			c = r1.test(b,n);
		for(int k=0;;++k){
			assert k<n*n;
			boolean change = false;
			for(int i=0; i<n; ++i)
				for(int j=0; j<n; ++j)
					if(!r[i][j] && c[i][j])
						change = r[i][j] = true;
			if(!change)
				return r;
			c = r1.test(b,n);
		}
	}

    @Override
    public void setRecursiveGroupId(int id){
        if(doRecurse){
            doRecurse = false;
            forceUpdateRecursiveGroupId = true;
            recursiveGroupId = id;
            r1.setRecursiveGroupId(id);
        }
    }

    @Override
    public int updateRecursiveGroupId(int parentId){
        if(forceUpdateRecursiveGroupId){
            forceUpdateRecursiveGroupId = false;
            int r1Id = r1.updateRecursiveGroupId(parentId | recursiveGroupId);
            recursiveGroupId |= r1Id & parentId;
        }
        return recursiveGroupId;
    }

    @Override
    public BoolExpr encode() {
        if(isEncoded){
            return ctx.mkTrue();
        }
        isEncoded = true;
        return r1.encode();
    }

    @Override
    protected BoolExpr encodeLFP() {
        return r1.encodeLFP();
    }

    @Override
    protected BoolExpr encodeIDL() {
        return r1.encodeIDL();
    }

    @Override
    protected BoolExpr encodeApprox() {
        return r1.encodeApprox();
    }

    @Override
    public BoolExpr encodeIteration(int recGroupId, int iteration){
        if(doRecurse){
            doRecurse = false;
            return r1.encodeIteration(recGroupId, iteration);
        }
        return ctx.mkTrue();
    }

    public BoolExpr encodeFinalIteration(int iteration){
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : encodeTupleSet){
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(tuple), edge(iteration,tuple)));
        }
        return enc;
    }
}
