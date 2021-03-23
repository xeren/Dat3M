package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.BoolExpr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class RelRangeIdentity extends UnaryRelation {

	private HashMap<Integer,ArrayList<Tuple>> bySecond;

    public static String makeTerm(Relation r1){
        return "[range(" + r1.getName() + ")]";
    }

    public RelRangeIdentity(Relation r1){
        super(r1);
        term = makeTerm(r1);
    }

    public RelRangeIdentity(Relation r1, String name) {
        super(r1, name);
        term = makeTerm(r1);
    }

	@Override
	protected void mkMaxTupleSet(){
		bySecond = new HashMap<>();
		for(Tuple t : r1.getMaxTupleSet()){
			Event e = t.getSecond();
			addMaxTuple(e,e);
			bySecond.computeIfAbsent(e.getCId(),k->new ArrayList<>()).add(t);
		}
	}

	@Override
	public void addEncodeTupleSet(Collection<Tuple> tuples){
        encodeTupleSet.addAll(tuples);
		HashSet<Tuple> activeSet = new HashSet<>(tuples);
        activeSet.retainAll(maxTupleSet);
        if(!activeSet.isEmpty()){
			HashSet<Tuple> r1Set = new HashSet<>();
            for(Tuple tuple : activeSet){
				r1Set.addAll(bySecond.get(tuple.getFirst().getCId()));
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
				if(c[j][i]){
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
			enc = ctx.mkAnd(enc,ctx.mkEq(edge(tuple1),
				ctx.mkOr(bySecond.get(tuple1.getFirst().getCId()).stream().map(r1::edge).toArray(BoolExpr[]::new))));
        }
        return enc;
    }
}
