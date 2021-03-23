package com.dat3m.dartagnan.wmm.relation.unary;

import com.microsoft.z3.BoolExpr;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 *
 * @author Florian Furbach
 */
public class RelInverse extends UnaryRelation {

    public static String makeTerm(Relation r1){
        return r1.getName() + "^-1";
    }

    public RelInverse(Relation r1){
        super(r1);
        term = makeTerm(r1);
    }

    public RelInverse(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

	@Override
	protected void mkMaxTupleSet(){
		for(Tuple pair : r1.getMaxTupleSet())
			addMaxTuple(pair.getSecond(),pair.getFirst());
	}

	@Override
	public void addEncodeTupleSet(Collection<Tuple> tuples){
        encodeTupleSet.addAll(tuples);
		HashSet<Tuple> activeSet = new HashSet<>(tuples);
        if(!activeSet.isEmpty()){
			HashSet<Tuple> invSet = new HashSet<>();
            for(Tuple pair : activeSet){
                invSet.add(new Tuple(pair.getSecond(), pair.getFirst()));
            }
            r1.addEncodeTupleSet(invSet);
        }
    }

	@Override
	public boolean[][] test(Map<Relation,boolean[][]> b, int n) {
		boolean[][] r = b.computeIfAbsent(this,k->new boolean[n][n]);
		boolean[][] c = r1.test(b,n);
		for(int i=0; i<n; ++i)
			for(int j=0; j<n; ++j)
				if(c[i][j])
					r[j][i] = true;
		return r;
	}

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        for(Tuple tuple : encodeTupleSet){
            Event e1 = tuple.getFirst();
            Event e2 = tuple.getSecond();
            enc = ctx.mkAnd(enc, ctx.mkEq(edge(e1, e2), r1.edge(e2, e1)));
        }
        return enc;
    }
}
    

