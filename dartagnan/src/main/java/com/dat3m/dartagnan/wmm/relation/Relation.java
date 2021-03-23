package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntExpr;
import java.util.*;

/**
 *
 * @author Florian Furbach
 */
public abstract class Relation {

    public static boolean PostFixApprox = false;

    protected String name;
    protected String term;

    protected Settings settings;
    protected Program program;
    protected Context ctx;

    protected boolean isEncoded;

	TupleSet maxTupleSet;
	protected HashMap<Event,HashSet<Event>> maxTupleSetTransitive;
	protected HashSet<Tuple> encodeTupleSet;

    protected int recursiveGroupId = 0;
    protected boolean forceUpdateRecursiveGroupId = false;
    protected boolean isRecursive = false;
    protected boolean forceDoEncode = false;

    public Relation() {}

    public Relation(String name) {
        this.name = name;
    }

    public int getRecursiveGroupId(){
        return recursiveGroupId;
    }

    public void setRecursiveGroupId(int id){
        forceUpdateRecursiveGroupId = true;
        recursiveGroupId = id;
    }

    public int updateRecursiveGroupId(int parentId){
        return recursiveGroupId;
    }

    public void initialise(Program program, Context ctx, Settings settings){
        this.program = program;
        this.ctx = ctx;
        this.settings = settings;
        this.maxTupleSet = null;
        maxTupleSetTransitive = null;
        this.isEncoded = false;
		encodeTupleSet = new HashSet<>();
    }

	public void initMaxTupleSet(){
		if(null==maxTupleSet){
			maxTupleSet = new TupleSet();
			mkMaxTupleSet();
		}
	}

	public Iterable<Tuple> getMaxTupleSet(){
		initMaxTupleSet();
		return maxTupleSet;
	}

	public int size(){
		initMaxTupleSet();
		return maxTupleSet.size();
	}

	public boolean contains(Event first, Event second){
		initMaxTupleSet();
		return maxTupleSet.contains(new Tuple(first,second));
	}

	public Collection<Tuple> getMaxTupleSet(Event first){
		initMaxTupleSet();
		return maxTupleSet.getByFirst(first);
	}

	public HashMap<Event,HashSet<Event>> getMaxTupleSetTransitive(){
		if(null!= maxTupleSetTransitive)
			return maxTupleSetTransitive;
		maxTupleSetTransitive = new HashMap<>();
		initMaxTupleSet();
		for(Tuple t : maxTupleSet){
			maxTupleSetTransitive.computeIfAbsent(t.getFirst(),k->new HashSet<>()).add(t.getSecond());
			maxTupleSetTransitive.computeIfAbsent(t.getSecond(),k->new HashSet<>());
		}
		boolean changed = true;
		while(changed){
			changed = false;
			for(Map.Entry<Event,HashSet<Event>> e : maxTupleSetTransitive.entrySet()){
				int c = e.getKey().getCId();
				HashSet<Event> next = new HashSet<>();
				for(Event x : e.getValue())
					if(c != x.getCId())
						next.addAll(maxTupleSetTransitive.get(x));
				changed |= e.getValue().addAll(next);
			}
		}
		return maxTupleSetTransitive;
	}

	protected abstract void mkMaxTupleSet();

	protected final void addMaxTuple(Event x, Event y){
		maxTupleSet.add(new Tuple(x,y));
	}

	protected void updateMaxTupleSetRecursive(){
	}

	public void getMaxTupleSetRecursive(){
		if(recursiveGroupId > 0 && null!=maxTupleSet){
			updateMaxTupleSetRecursive();
		}
	}

	public Set<Tuple> getEncodeTupleSet(){
        return encodeTupleSet;
    }

	public void addEncodeTupleSet(Collection<Tuple> tuples){
        encodeTupleSet.addAll(tuples);
    }

    public String getName() {
        if(name != null){
            return name;
        }
        return term;
    }

    public Relation setName(String name){
        this.name = name;
        return this;
    }

    public String getTerm(){
        return term;
    }

    public boolean getIsNamed(){
        return name != null;
    }

    private BoolExpr edge(int first, int second) {
        return ctx.mkBoolConst("edge "+getName()+" "+first+" "+second);
    }

    public BoolExpr edge(Event first, Event second) {
        return edge(first.getCId(),second.getCId());
    }

    public BoolExpr edge(Tuple tuple) {
        return edge(tuple.getFirst(),tuple.getSecond());
    }

    private BoolExpr edge(int iteration, int first, int second) {
        return ctx.mkBoolConst("edge"+iteration+" "+getName()+" "+first+" "+second);
    }

    public BoolExpr edge(int iteration, Event first, Event second) {
        return edge(iteration,first.getCId(),second.getCId());
    }

    public BoolExpr edge(int iteration, Tuple tuple) {
        return edge(iteration,tuple.getFirst(),tuple.getSecond());
    }

    public IntExpr intCount(Event first, Event second) {
        return ctx.mkIntConst("level "+getName()+" "+first.getCId()+" "+second.getCId());
    }

    @Override
    public String toString(){
        if(name != null){
            return name + " := " + term;
        }
        return term;
    }

    @Override
    public int hashCode(){
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

        return getName().equals(((Relation)obj).getName());
    }

    public BoolExpr encode() {
        if(isEncoded){
            return ctx.mkTrue();
        }
        isEncoded = true;
        return doEncode();
    }

    protected BoolExpr encodeLFP() {
        return encodeApprox();
    }

    protected BoolExpr encodeIDL() {
        return encodeApprox();
    }

    protected abstract BoolExpr encodeApprox();

    public BoolExpr encodeIteration(int recGroupId, int iteration){
        return ctx.mkTrue();
    }

    protected BoolExpr doEncode(){
		assert null==maxTupleSet ? encodeTupleSet.isEmpty() : maxTupleSet.containsAll(encodeTupleSet);
        BoolExpr enc = ctx.mkTrue();
        if(!encodeTupleSet.isEmpty() || forceDoEncode){
            if(settings.getMode() == Mode.KLEENE) {
                return ctx.mkAnd(enc, encodeLFP());
            } else if(settings.getMode() == Mode.IDL) {
                return ctx.mkAnd(enc, encodeIDL());
            }
            return ctx.mkAnd(enc, encodeApprox());
        }
        return enc;
    }
}
