package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.utils.Settings;
import com.microsoft.z3.*;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.dat3m.dartagnan.wmm.utils.Utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;


/**
 *
 * @author Florian Furbach
 */
public abstract class Relation {

    public static boolean PostFixApprox = false;

    protected String name;
    protected String term;

    protected Program program;
    protected Context ctx;
    protected Sort eventSort;

    protected TupleSet maxTupleSet;
    protected TupleSet encodeTupleSet;

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
        this.maxTupleSet = null;
        this.eventSort = ctx.mkIntSort();//ctx.mkUninterpretedSort("Event");
        encodeTupleSet = new TupleSet();
    }

    public abstract TupleSet getMaxTupleSet();

    public TupleSet getMaxTupleSetRecursive(){
        return getMaxTupleSet();
    }

    public TupleSet getEncodeTupleSet(){
        return encodeTupleSet;
    }

    public void addEncodeTupleSet(TupleSet tuples){
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

    /**
     * Describes this relation's contents.
     * @param context
     * Utility used to create propositions and to specialize the encoding for the current program.
     * @return
     * Proposition that this relation contains only those tuples according to its semantics.
     */
    public BoolExpr encode(EncodeContext context) {
        if(!context.add(this))
            return ctx.mkTrue();
        return doEncode(context);
    }

    protected BoolExpr encodeLFP(EncodeContext context) {
        return encodeApprox(context);
    }

    protected BoolExpr encodeIDL(EncodeContext context) {
        return encodeApprox(context);
    }

    /**
     * Describes this relation's content using first order logic.
     * @param context
     * Utility used to create propositions and to specialize the encoding for the current program.
     * @return
     * Proposition that this relation contains only those tuples according to its semantics.
     */
    protected abstract BoolExpr encodeFirstOrder(EncodeContext context);

    protected abstract BoolExpr encodeApprox(EncodeContext context);

    public BoolExpr encodeIteration(int recGroupId, int iteration){
        return ctx.mkTrue();
    }

    protected BoolExpr doEncode(EncodeContext context){
        BoolExpr enc = encodeNegations();
        if(encodeTupleSet.isEmpty() && !forceDoEncode)
            return enc;
        switch(context.settings.getMode())
        {
            case KLEENE:
            return ctx.mkAnd(enc, encodeLFP(context));
            case IDL:
            return ctx.mkAnd(enc, encodeIDL(context));
            case FO:
            return encodeFirstOrder(context);
            default:
            return ctx.mkAnd(enc, encodeApprox(context));
        }
    }

    /**
     * Assume that all tuples in encodeTupleSet that do not belong to maxTupleSet are not contained by this relation.
     * Also remove those tuples from encodeTupleSet.
     * @return
     * Proposition that all tuples that have to be encoded for this relation
     * and that do not appear in the over-approximation of its externalization
     * are not contained by this relation.
     */
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

    /**
     * Creates an atomic formula for a relationship.
     * @param first
     * Event in this domain set.
     * @param second
     * Event in this range set.
     * @return
     * Proposition that the specified event pair is contained by this relation.
     * @see #edge(Tuple)
     */
    public BoolExpr edge(Event first, Event second) {
        return Utils.edge(getName(), first, second, ctx);
    }

    /**
     * Creates an atomic formula for a relationship.
     * @param t
     * Pair of events.
     * @return
     * Proposition that the pair is contained by this relation.
     * @see #edge(Event,Event)
     */
    public BoolExpr edge(Tuple t) {
        return edge(t.getFirst(), t.getSecond());
    }

    /**
     * Creates an atomic formula for a relationship.
     * Used in the Kleene/LFP variant for recursion evaluation.
     * @param iteration
     * Index of the associated version of this relation.
     * Between 0 and the iteration count of the {@link com.dat3m.dartagnan.wmm.utils.RecursiveGroup} in context.
     * @param first
     * Event in this domain set.
     * @param second
     * Event in this range set.
     * @return
     * Proposition that the pair is contained by this relation's version.
     * @see Relation#edge(int,Tuple)
     */
    public BoolExpr edge(int iteration, Event first, Event second) {
        return Utils.edge(getName() + "_" + iteration, first, second, ctx);
    }

    /**
     * Creates an atomic formula for a relationship.
     * Used in the Kleene/LFP variant for recursion evaluation.
     * @param iteration
     * Index of the associated version of this relation.
     * Between 0 and the iteration count of the {@link com.dat3m.dartagnan.wmm.utils.RecursiveGroup} in context.
     * @param t
     * Pair of events.
     * @return
     * Proposition that the pair is contained by this relation's version.
     */
    public BoolExpr edge(int iteration, Tuple t) {
        return edge(iteration, t.getFirst(), t.getSecond());
    }

    /**
     * Creates an atomic formula for a relationship.
     * Used by the First-Order/Free variant for recursion evaluation.
     * @param first
     * Event in this domain set.
     * @param second
     * Event in this range set.
     * @return
     * Proposition that the pair is contained by this relation.
     */
    public BoolExpr edge(Expr first, Expr second) {
        return(BoolExpr)ctx.mkFuncDecl(getName(), new Sort[]{eventSort, eventSort}, ctx.mkBoolSort()).apply(first, second);
    }

    /**
     * Used to describe a total order induced by this relation.
     * Yields a monotone mapping into the integer order.
     * @param e
     * Some event.
     * @return
     * Integer equally ordered with respect to other events.
     */
    public IntExpr intVar(Event e) {
        return Utils.intVar(getName(), e, ctx);
    }

    /**
     * Associates an integer with a pair of events.
     * Used by Integer Difference Logic (IDL) encoding for recursion evaluation.
     * @param first
     * Event in this domain set.
     * @param second
     * Event in this range set.
     * @return
     * Integer.
     */
    public IntExpr intCount(Event first, Event second) {
        return Utils.intCount(getName(), first, second, ctx);
    }

    public IntExpr intCount(Tuple t) {
        return intCount(t.getFirst(), t.getSecond());
    }

    @FunctionalInterface
    protected interface UnaryBody {
        BoolExpr of(Expr event);
    }

    @FunctionalInterface
    protected interface UnaryPattern {
        Pattern of(Expr event);
    }

    protected final BoolExpr exists(int depth, UnaryBody body, UnaryPattern... pattern) {
        Expr a = ctx.mkConst("x" + depth, eventSort);
        return ctx.mkExists(new Expr[]{a}, body.of(a), 0,
                Arrays.stream(pattern).map(p->p.of(a)).toArray(Pattern[]::new),
                null, null, null);
    }

    protected final BoolExpr forall(int depth, UnaryBody body, UnaryPattern... pattern) {
        Expr a = ctx.mkConst("x" + depth, eventSort);
        return ctx.mkForall(new Expr[]{a}, body.of(a), 0,
                Arrays.stream(pattern).map(p->p.of(a)).toArray(Pattern[]::new),
                null, null, null);
    }

    @FunctionalInterface
    protected interface BinaryBody {
        BoolExpr of(Expr first, Expr second);
    }

    @FunctionalInterface
    protected interface BinaryPattern {
        Pattern of(Expr first, Expr second);
    }

    protected final BoolExpr forall(int depth, BinaryBody body, BinaryPattern... pattern) {
        Expr a = ctx.mkConst("x" + depth, eventSort);
        Expr b = ctx.mkConst("x" + (depth + 1), eventSort);
        return ctx.mkForall(new Expr[]{a, b}, body.of(a, b), 0,
                Arrays.stream(pattern).map(p->p.of(a, b)).toArray(Pattern[]::new),
                null, null, null);
    }

    @FunctionalInterface
    protected interface TernaryBody {
        BoolExpr of(Expr first, Expr second, Expr third);
    }

    @FunctionalInterface
    protected interface TernaryPattern {
        Pattern of(Expr first, Expr second, Expr third);
    }

    protected final BoolExpr forall(int depth, TernaryBody body, TernaryPattern... pattern) {
        Expr a = ctx.mkConst("x" + depth, eventSort);
        Expr b = ctx.mkConst("x" + (depth + 1), eventSort);
        Expr c = ctx.mkConst("x" + (depth + 2), eventSort);
        return ctx.mkForall(new Expr[]{a, b, c}, body.of(a, b, c), 0,
                Arrays.stream(pattern).map(p->p.of(a, b, c)).toArray(Pattern[]::new),
                null, null, null);
    }

    protected final BoolExpr and(Stream<?extends BoolExpr> operands) {
        return ctx.mkAnd(operands.toArray(BoolExpr[]::new));
    }

    protected final BoolExpr or(Stream<?extends BoolExpr> operands) {
        return ctx.mkOr(operands.toArray(BoolExpr[]::new));
    }
}
