package com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example;

import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.DerivedDefinition;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.EncodingContext;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.collect.Sets.union;
import static java.util.stream.Collectors.*;
import static java.util.stream.IntStream.range;

/**
 * Describes a constraint <mathml><mi>R</mi><mo>=</mo><mi>R</mi><sub><mn>0</mn></sub><mo>;</mo><mi>R</mi><sub><mn>1</mn></sub><mo>;...;</mo><mi>R</mi><sub><mi>n</mi></sub></mathml>.
 * There are several inference rules on knowledge closure implemented in this class.
 * The <i>outer tuple</i> refers to a pair in the defined relation.
 * A <i>path</i> maps each dependency to a pair of events,
 * such that all neighbor pairs are adjacent.
 * There is a function from paths to outer tuples.
 * An <i>inner pair</i> is a relationship of a dependency.
 * <ol>
 * <li>
 * If all paths with a common outer tuple
 * have at least one disabled inner pair,
 * then the common outer tuple is disabled.
 * <li>
 * If all inner pairs of a path are enabled
 * and its outer tuple implies all participating events,
 * then the outer tuple is enabled.
 * <li>
 * If the outer tuple of a path with a certain inner pair is disabled,
 * all other inner pairs are enabled
 * and the certain inner pair implies all participating events,
 * then the certain inner pair is disabled.
 * <li>
 * If an outer tuple is enabled
 * and all its paths share a common inner pair,
 * which implies all participating events,
 * then the common inner pair is enabled.
 * </ol>
 */
public final class Composition extends DerivedDefinition {

    public Composition(Relation definedRel, List<Relation> unionRels) {
        super(definedRel, unionRels);
    }

    public Composition(Relation definedRel, Relation... dependencies) {
        this(definedRel, Arrays.asList(dependencies));
    }

    @Override
    protected String getOperationSymbol() {
        return " ; ";
    }

    @Override
    protected Map<Relation,Knowledge.Delta> bottomUpKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation,Knowledge> know) {
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        List<Relation> deps = getDependencies();

        int index = deps.indexOf(changed);
        int end = deps.size() - index - 1;
        List<Knowledge> lhs = transform(deps.subList(0,index),know::get);
        assert lhs.size() == index;
        List<Knowledge> rhs = transform(deps.subList(index+1,deps.size()),know::get);
        assert rhs.size() == end;

        //rule 1
        //part 1: collect candidates for outer tuples to be disabled
        List<TupleSet> mayLeft = transform(lhs,Knowledge::getMaySet);
        List<TupleSet> mayRight = transform(rhs,Knowledge::getMaySet);
        TupleSet disable = new TupleSet();
        for(Tuple tuple : delta.getDisabledSet()) {
            List<List<Event>> left = leftProduct(tuple.getFirst(),mayLeft);
            if(left.isEmpty()) {
                continue;
            }
            for(List<Event> r : rightProduct(tuple.getSecond(),mayRight)) {
                assert r.size() == end + 1;
                Event z = r.get(end);
                for(List<Event> l : left) {
                    assert l.size() == index + 1;
                    disable.add(new Tuple(l.get(0),z));
                }
            }
        }
        //part 2: filter for outer tuples without remaining paths
        TupleSet mayCenter = know.get(changed).getMaySet();
        Iterator<Tuple> iterator = disable.iterator();
        while(iterator.hasNext()) {
            Tuple tuple = iterator.next();
            List<Event> left = rightProduct(tuple.getFirst(),mayLeft).stream()
            .map(l->l.get(l.size()-1))
            .distinct()
            .collect(toList());
            if(!left.isEmpty()
                    && leftProduct(tuple.getSecond(),mayRight).stream()
                    .map(x->x.get(0))
                    .distinct()
                    .anyMatch(x->left.stream().anyMatch(z->mayCenter.contains(new Tuple(x,z))))) {
                iterator.remove();
            }
        }

        //rule 2
        List<TupleSet> mustLeft = transform(lhs,Knowledge::getMustSet);
        List<TupleSet> mustRight = transform(rhs,Knowledge::getMustSet);
        TupleSet enable = new TupleSet();
        for(Tuple tuple : delta.getEnabledSet()) {
            List<List<Event>> left = leftProduct(tuple.getFirst(),mustLeft);
            if(left.isEmpty()) {
                continue;
            }
            for(List<Event> r : rightProduct(tuple.getSecond(),mustRight)) {
                assert r.size() == end + 1;
                Event z = r.get(end);
                for(List<Event> l : left) {
                    assert l.size() == index + 1;
                    Event x = l.get(0);
                    if(Stream.of(l.subList(0,index),r.subList(1,end+1))
                            .flatMap(List::stream).allMatch(y->isImplied(exec,x,z,y))) {
                        enable.add(new Tuple(x,z));
                    }
                }
            }
        }

        HashMap<Relation, Knowledge.Delta> result = new HashMap<>();
        result.put(definedRelation,new Knowledge.Delta(disable,enable));

        //rule 3
        TupleSet mayOuter = know.get(definedRelation).getMaySet();
        for(int i = 0; i < index; i++) {
            TupleSet dis = new TupleSet();
            List<TupleSet> mustLeftAlt = Stream.of(
                    mustLeft.subList(0,i),
                    List.of(mayLeft.get(i)),
                    mustLeft.subList(i+1,index))
            .flatMap(List::stream)
            .collect(toList());
            assert mustLeftAlt.size() == index;
            for(Tuple tuple : delta.getEnabledSet()) {
                List<List<Event>> right = rightProduct(tuple.getSecond(),mustRight);
                if(right.isEmpty()) {
                    continue;
                }
                for(List<Event> l : leftProduct(tuple.getFirst(),mustLeftAlt)) {
                    assert l.size() == index + 1;
                    Event x = l.get(0);
                    Event y0 = l.get(i);
                    Event y1 = l.get(i+1);
                    Predicate<Event> implied = y -> isImplied(exec,y0,y1,y);
                    if(l.subList(0,i).stream().allMatch(implied)
                            && l.subList(i+2,index+1).stream().allMatch(implied)
                            && right.stream().anyMatch(r->!mayOuter.contains(new Tuple(x,r.get(end)))
                                    && r.stream().allMatch(implied))) {
                        dis.add(new Tuple(y0,y1));
                    }
                }
            }
            if(!dis.isEmpty()) {
                result.put(deps.get(i),new Knowledge.Delta(dis,new TupleSet()));
            }
        }
        for(int i = 0; i < end; i++) {
            TupleSet dis = new TupleSet();
            List<TupleSet> mustRightAlt = Stream.of(
                    mustRight.subList(0,i),
                    List.of(mayRight.get(i)),
                    mustRight.subList(i+1,end))
            .flatMap(List::stream)
            .collect(toList());
            assert mustRightAlt.size() == end;
            for(Tuple tuple : delta.getEnabledSet()) {
                List<List<Event>> left = leftProduct(tuple.getFirst(),mustLeft);
                if(left.isEmpty()) {
                    continue;
                }
                for(List<Event> r : rightProduct(tuple.getSecond(),mustRightAlt)) {
                    assert r.size() == end + 1;
                    Event z = r.get(end);
                    Event y0 = r.get(i);
                    Event y1 = r.get(i+1);
                    Predicate<Event> implied = y -> isImplied(exec,y0,y1,y);
                    if(r.subList(0,i).stream().allMatch(implied)
                            && r.subList(i+2,end+1).stream().allMatch(implied)
                            && left.stream().anyMatch(l->!mayOuter.contains(new Tuple(l.get(0),z))
                                    && l.stream().allMatch(implied))) {
                        dis.add(new Tuple(y0,y1));
                    }
                }
            }
            if(!dis.isEmpty()) {
                result.put(deps.get(i+index+1),new Knowledge.Delta(dis,new TupleSet()));
            }
        }

        //TODO rule 4
        return result;
    }

    @Override
    protected Map<Relation,Knowledge.Delta> topDownKnowledgeClosure(Relation changed, Knowledge.Delta delta, Map<Relation,Knowledge> know) {
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        List<Relation> deps = getDependencies();
        List<TupleSet> must = transform(deps,d->know.get(d).getMustSet());
        Map<Relation,Knowledge.Delta> result = new HashMap<>();

        //rule 3
        for(int index = 0; index < deps.size(); index++) {
            TupleSet disable = new TupleSet();
            List<TupleSet> mustLeft = must.subList(0,index);
            List<TupleSet> mustRight = must.subList(index+1,deps.size());
            for(Tuple tuple : delta.getDisabledSet()) {
                List<List<Event>> left = rightProduct(tuple.getFirst(),mustLeft);
                if(left.isEmpty()) {
                    continue;
                }
                for(List<Event> r : leftProduct(tuple.getSecond(),mustRight)) {
                    assert r.size() == deps.size() - index;
                    Event z = r.get(0);
                    for(List<Event> l : left) {
                        assert l.size() == index + 1;
                        Event x = l.get(index);
                        if(Stream.of(l.subList(0,index),r.subList(1,r.size()))
                                .flatMap(List::stream)
                                .allMatch(y->isImplied(exec,x,z,y))) {
                            disable.add(new Tuple(x,z));
                        }
                    }
                }
            }
            result.put(deps.get(index),new Knowledge.Delta(disable,new TupleSet()));
        }

        //TODO rule 4
        return result;
    }

    @Override
    public Knowledge computeInitialDefiningKnowledge(Map<Relation,Knowledge> know) {
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        List<Relation> deps = getDependencies();

        //optimization: fully iterate smallest child
        List<Knowledge> c = transform(deps,know::get);
        List<TupleSet> m = transform(c,Knowledge::getMaySet);
        int index = range(0,deps.size()).boxed().min(Comparator.comparingInt(i->m.get(i).size())).orElseThrow();
        int end = deps.size() - index - 1;
        List<TupleSet> mayLeft = m.subList(0,index);
        List<TupleSet> mayRight = m.subList(index+1,deps.size());
        TupleSet center = c.get(index).getMustSet();

        TupleSet may = new TupleSet();
        TupleSet must = new TupleSet();
        for(Tuple tuple : m.get(index)) {
            List<List<Event>> left = leftProduct(tuple.getFirst(),mayLeft);
            if(left.isEmpty()) {
                continue;
            }
            boolean isTrue = center.contains(tuple);
            for(List<Event> r : rightProduct(tuple.getSecond(),mayRight)) {
                assert r.size() == end + 1;
                Event z = r.get(end);
                List<Event> rest = r.subList(0,end);
                boolean rightTrue = isTrue
                        && range(0,end)
                        .allMatch(i->c.get(index+1+i).isTrue(new Tuple(r.get(i),r.get(i+1))));
                for(List<Event> l : left) {
                    assert l.size() == index + 1;
                    Event x =  l.get(0);
                    Tuple t = new Tuple(x,z);
                    may.add(t);
                    if(rightTrue
                            && range(0,index)
                            .allMatch(i->c.get(i).isTrue(new Tuple(l.get(i),l.get(i+1))))
                            && Stream.of(l.subList(1,index+1),rest)
                            .flatMap(List::stream)
                            .allMatch(y -> isImplied(exec,x,z,y))) {
                        must.add(t);
                    }
                }
            }
        }
        return new Knowledge(may,must);
    }

    @Override
    public Knowledge.SetDelta computeIncrementalDefiningKnowledge(Relation changed, Knowledge.SetDelta delta, Map<Relation,Knowledge> know) {
        ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        List<Relation> deps = getDependencies();

        int index = deps.indexOf(changed);
        int end = deps.size() - index - 1;
        List<Knowledge> lhs = transform(deps.subList(0,index),know::get);
        List<Knowledge> rhs = transform(deps.subList(index+1,deps.size()),know::get);
        List<TupleSet> mayLeft = transform(lhs,Knowledge::getMaySet);
        List<TupleSet> mayRight = transform(rhs,Knowledge::getMaySet);

        TupleSet may = new TupleSet();
        TupleSet must = new TupleSet();
        for(Tuple tuple : union(delta.getAddedMaySet(),delta.getAddedMustSet())) {
            List<List<Event>> left = leftProduct(tuple.getFirst(),mayLeft);
            if(left.isEmpty()) {
                continue;
            }
            List<List<Event>> right = rightProduct(tuple.getSecond(),mayRight);
            if(right.isEmpty()) {
                continue;
            }
            boolean isTrue = delta.getAddedMustSet().contains(tuple);
            boolean notFalse = delta.getAddedMaySet().contains(tuple);
            assert isTrue || notFalse;
            for(List<Event> r : right) {
                assert r.size() == end + 1;
                Event z = r.get(r.size()-1);
                List<Event> rest = r.subList(0,r.size()-1);
                boolean rightTrue = isTrue
                && range(0,end)
                .allMatch(i->rhs.get(i).isTrue(new Tuple(r.get(i),r.get(i+1))));
                for(List<Event> l : left) {
                    assert l.size() == index + 1;
                    Event x =  l.get(0);
                    Tuple t = new Tuple(x,z);
                    if(notFalse) {
                        may.add(t);
                    }
                    if(rightTrue
                            && range(0,index)
                            .allMatch(i->lhs.get(i).isTrue(new Tuple(l.get(i),l.get(i+1))))
                            && Stream.of(l.subList(1,l.size()),rest)
                            .flatMap(List::stream)
                            .allMatch(y -> isImplied(exec,x,z,y))) {
                        must.add(t);
                    }
                }
            }
        }
        return new Knowledge.SetDelta(may,must);
    }

    @Override
    public Map<Relation,TupleSet> propagateActiveSet(TupleSet activeSet, Map<Relation,Knowledge> know) {
        List<Relation> deps = getDependencies();

        //optimization: test largest child
        List<TupleSet> may = transform(deps,d->know.get(d).getMaySet());
        int index = range(0,deps.size()).boxed().max(Comparator.comparingInt(i->may.get(i).size())).orElseThrow();
        List<TupleSet> lhs = may.subList(0,index);
        List<TupleSet> rhs = may.subList(index+1,deps.size());
        TupleSet center = may.get(index);

        List<TupleSet> result = new ArrayList<>();
        for(Relation ignored: deps) {
            result.add(new TupleSet());
        }
        TupleSet centerResult = result.get(index);
        for(Tuple tuple : activeSet) {
            List<List<Event>> right = leftProduct(tuple.getSecond(),rhs);
            if(right.isEmpty()) {
                continue;
            }
            for(List<Event> l : rightProduct(tuple.getFirst(),lhs)) {
                for(List<Event> r : right) {
                    Tuple t = new Tuple(l.get(l.size()-1),r.get(0));
                    if(!center.contains(t)) {
                        continue;
                    }
                    centerResult.add(t);
                    for(int i = 0; i < index; i++) {
                        result.get(i).add(new Tuple(l.get(i),l.get(i+1)));
                    }
                    for(int i = 0; i < rhs.size(); i++) {
                        result.get(index+1+i).add(new Tuple(r.get(i),r.get(i+1)));
                    }
                }
            }
        }
        return range(0,deps.size())
        .boxed()
        .collect(toMap(deps::get,result::get));
    }

    @Override
    public BooleanFormula encodeDefinitions(TupleSet toBeEncoded, Map<Relation,Knowledge> know, EncodingContext ctx) {
        List<Relation> deps = getDependencies();
        BooleanFormulaManager bmgr = ctx.getBmgr();

        //optimization: test largest child
        List<Knowledge> k = transform(deps,know::get);
        List<TupleSet> may = transform(k,Knowledge::getMaySet);
        int index = range(0,may.size()).boxed().max(Comparator.comparingInt(i->may.get(i).size())).orElseThrow();
        List<TupleSet> mayLeft = may.subList(0,index);
        List<TupleSet> mayRight = may.subList(index+1,deps.size());
        TupleSet mayCenter = may.get(index);
        Knowledge knowledge = know.get(definedRelation);

        BooleanFormula enc = bmgr.makeTrue();
        for(Tuple tuple : toBeEncoded) {
            List<List<Event>> right = leftProduct(tuple.getSecond(),mayRight);
            if(right.isEmpty()) {
                continue;
            }
            BooleanFormula clause = bmgr.makeFalse();
            for(List<Event> l : rightProduct(tuple.getFirst(),mayLeft)) {
                for(List<Event> r : right) {
                    Tuple t = new Tuple(l.get(l.size()-1),r.get(0));
                    if(!mayCenter.contains(t)) {
                        continue;
                    }
                    BooleanFormula path = deps.get(index).getSMTVar(t,k.get(index),ctx);
                    for(int i = 0; i < index; i++) {
                        path = bmgr.and(path,deps.get(i)
                                .getSMTVar(new Tuple(l.get(i),l.get(i+1)),k.get(i),ctx));
                    }
                    for(int i = 0; i < mayRight.size(); i++) {
                        path = bmgr.and(path,deps.get(index+1+i)
                                .getSMTVar(new Tuple(r.get(i),r.get(i+1)),k.get(index+1+i),ctx));
                    }
                    clause = bmgr.or(clause,path);
                }
            }
            enc = bmgr.and(enc,bmgr.equivalence(getSMTVar(tuple,knowledge,ctx),clause));
        }
        return enc;
    }

    private static List<List<Event>> leftProduct(Event start, List<TupleSet> tupleSets) {
        ArrayList<List<Event>> result = new ArrayList<>();
        leftProduct(new LinkedList<>(List.of(start)),new LinkedList<>(tupleSets),result);
        return result;
    }

    private static void leftProduct(LinkedList<Event> start, LinkedList<TupleSet> tupleSets, List<List<Event>> out) {
        if(tupleSets.isEmpty()) {
            out.add(List.copyOf(start));
            return;
        }
        TupleSet last = tupleSets.pollLast();
        for(Tuple tuple : last.getBySecond(start.getFirst())) {
            start.addFirst(tuple.getFirst());
            leftProduct(start,tupleSets,out);
            start.removeFirst();
        }
        tupleSets.addLast(last);
    }

    private static List<List<Event>> rightProduct(Event start, List<TupleSet> tupleSets) {
        ArrayList<List<Event>> result = new ArrayList<>();
        rightProduct(new LinkedList<>(List.of(start)),new LinkedList<>(tupleSets),result);
        return result;
    }

    private static void rightProduct(LinkedList<Event> start, LinkedList<TupleSet> tupleSets, List<List<Event>> out) {
        if(tupleSets.isEmpty()) {
            out.add(List.copyOf(start));
            return;
        }
        TupleSet first = tupleSets.pollFirst();
        for(Tuple tuple : first.getByFirst(start.getLast())) {
            start.addLast(tuple.getSecond());
            rightProduct(start,tupleSets,out);
            start.removeLast();
        }
        tupleSets.addFirst(first);
    }

    private static <A,B> List<B> transform(Collection<?extends A> source, Function<?super A,?extends B> function) {
        return source.stream().map(function).collect(toList());
    }

    private static boolean isImplied(ExecutionAnalysis a, Event premise0, Event premise1, Event conclusion) {
        return a.isImplied(premise0,conclusion) || a.isImplied(premise1,conclusion);
    }
}
