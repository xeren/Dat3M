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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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

    protected TupleSet maxTupleSet;
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
        this.isEncoded = false;
		encodeTupleSet = new HashSet<>();
    }

	public TupleSet getMaxTupleSet(){
		if(null==maxTupleSet){
			maxTupleSet = new TupleSet();
			mkMaxTupleSet();
		}
		return maxTupleSet;
	}

	protected abstract void mkMaxTupleSet();

	protected final void addMaxTuple(Event x, Event y){
		maxTupleSet.add(new Tuple(x,y));
	}

    public TupleSet getMaxTupleSetRecursive(){
        return getMaxTupleSet();
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
        BoolExpr enc = encodeNegations();
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

    private BoolExpr encodeNegations(){
        BoolExpr enc = ctx.mkTrue();
        if(!encodeTupleSet.isEmpty()){
            Set<Tuple> negations = new HashSet<>(encodeTupleSet);
            negations.removeAll(maxTupleSet);
            for(Tuple tuple : negations){
                enc = ctx.mkAnd(enc, ctx.mkNot(edge(tuple)));
            }
            encodeTupleSet.removeAll(negations);
        }
        return enc;
    }
}
