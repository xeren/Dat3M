package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RelDomainIdentity extends UnaryRelation {

    public static String makeTerm(Relation r1){
        return "[domain(" + r1.getName() + ")]";
    }

    public RelDomainIdentity(Relation r1){
        super(r1);
        term = makeTerm(r1);
    }

    public RelDomainIdentity(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Tuple tuple : r1.getMaxTupleSet()){
                maxTupleSet.add(new Tuple(tuple.getFirst(), tuple.getFirst()));
            }
        }
        return maxTupleSet;
    }

    @Override
    public void addEncodeTupleSet(TupleSet tuples){
        encodeTupleSet.addAll(tuples);
        Set<Tuple> activeSet = new HashSet<>(tuples);
        activeSet.retainAll(maxTupleSet);
        if(!activeSet.isEmpty()){
            TupleSet r1Set = new TupleSet();
            for(Tuple tuple : activeSet){
                r1Set.addAll(r1.getMaxTupleSet().getByFirst(tuple.getFirst()));
            }
            r1.addEncodeTupleSet(r1Set);
        }
    }

	@Override
	public boolean[][] test(Map<Relation,boolean[][]> b, int n) {
		boolean[][] r = b.computeIfAbsent(this,k->new boolean[n][n]);
		boolean[][] c = r1.test(b,n);
		for(int i=0; i<n; ++i){
			if(r[i][i])
				continue;
			for(int j=0; j<n; ++j)
				if(c[i][j]){
					r[i][i] = true;
					break;
				}
		}
		return r;
	}

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple1 : encodeTupleSet){
            Event e = tuple1.getFirst();
            BoolExpr opt = ctx.mkFalse();
            for(Tuple tuple2 : r1.getMaxTupleSet().getByFirst(e)){
                opt = ctx.mkOr(r1.edge(tuple2));
            }
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e, e), opt));
        }
        return enc;
    }
}
