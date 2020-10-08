package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.microsoft.z3.BoolExpr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class EdgeTestHelper {

    private Program program;
    private Relation relation;
    private FilterAbstract filter1;
    private FilterAbstract filter2;

    public EdgeTestHelper(Program program, Relation relation, FilterAbstract filter1, FilterAbstract filter2){
        this.program = program;
        this.relation = relation;
        this.filter1 = filter1;
        this.filter2 = filter2;
    }

    // Encode violation of expected event pairs in the relation
    public BoolExpr encodeIllegalEdges(EncodeContext context, ProgramCache program, int[] data){
        Set<Tuple> all = mkAllTuples();
        Set<Tuple> max = relation.getMaxTupleSet(program);
        Set<Tuple> expected = mkExpectedTuples(all, data);
        ArrayList<BoolExpr> enc = new ArrayList<>();
        String name = relation.getName();
        for(Tuple tuple : all){
            BoolExpr edge = context.edge(name, tuple.getFirst(), tuple.getSecond());
            if(expected.contains(tuple)){
                enc.add(context.not(edge));
            } else if(max.contains(tuple)){
                enc.add(edge);
            }
        }
        return context.or(enc);
    }

    // Generate set of all possible pairs (can be greater than maxTupleSet of the relation)
    private Set<Tuple> mkAllTuples(){
        Set<Tuple> result = new HashSet<>();
        for(Event e1 : program.getCache().getEvents(filter1)){
            for(Event e2 : program.getCache().getEvents(filter2)){
                result.add(new Tuple(e1, e2));
            }
        }
        return result;
    }

    private static final class IntPair
    {
        final int first;
        final int second;
        IntPair(int f, int s) {
            first = f;
            second = s;
        }
        @Override
        public boolean equals(Object other)
        {
            return other instanceof IntPair && first == ((IntPair)other).first && second == ((IntPair)other).second;
        }
        @Override
        public int hashCode() {
            return first << 16 & second;
        }
    }

    // Convert expected result to a set of tuples
    private Set<Tuple> mkExpectedTuples(Set<Tuple> all, int[] data){
        if(data.length % 2 == 1){
            throw new IllegalArgumentException("Invalid definition of expected edges");
        }

        HashSet<IntPair> map = new HashSet<>();
        for(int i = 0; i < data.length; i += 2){
            map.add(new IntPair(data[i], data[i + 1]));
        }

        Set<Tuple> result = new HashSet<>();
        for(Tuple tuple : all){
            int id1 = tuple.getFirst().getOId();
            int id2 = tuple.getSecond().getOId();
            if(map.contains(new IntPair(id1, id2))){
                result.add(tuple);
            }
        }
        return result;
    }
}
