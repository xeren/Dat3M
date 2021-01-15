package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.program.event.RMWStore;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Filter;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Flag;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;

public class RelRMW extends StaticRelation {

    private final Filter loadFilter  = Filter.And.of(Filter.of(EType.EXCLUSIVE), Filter.Atom.read);

    private final Filter storeFilter = Filter.And.of(Filter.of(EType.EXCLUSIVE), Filter.Atom.write);

    // Set without exclusive events
    private TupleSet baseMaxTupleSet;

    public RelRMW(){
        term = "rmw";
        forceDoEncode = true;
    }

    @Override
    public void initialise(Program program, Context ctx, Settings settings){
        super.initialise(program, ctx, settings);
        this.baseMaxTupleSet = null;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            baseMaxTupleSet = new TupleSet();
            for(RMWStore store : program.getCache().getEvents(RMWStore.class))
            	baseMaxTupleSet.add(new Tuple(store.loadEvent, store));

            maxTupleSet = new TupleSet();
            maxTupleSet.addAll(baseMaxTupleSet);

            for(Thread thread : program.getThreads()){
                for(Event load : thread.getCache().getEvents(loadFilter)){
                    for(Event store : thread.getCache().getEvents(storeFilter)){
                        if(load.getCId() < store.getCId()){
                            maxTupleSet.add(new Tuple(load, store));
                        }
                    }
                }
            }
        }       	
        return maxTupleSet;
    }

    @Override
    protected BoolExpr encodeApprox() {
        // Encode base (not exclusive pairs) RMW
        TupleSet origEncodeTupleSet = encodeTupleSet;
        encodeTupleSet = baseMaxTupleSet;
        BoolExpr enc = super.encodeApprox();
        encodeTupleSet = origEncodeTupleSet;

        // Encode RMW for exclusive pairs
        BoolExpr unpredictable = ctx.mkFalse();
        for(Thread thread : program.getThreads()) {
            for (Event store : thread.getCache().getEvents(storeFilter)) {
                BoolExpr storeExec = ctx.mkFalse();
                for (Event load : thread.getCache().getEvents(loadFilter)) {
                    if (load.getCId() < store.getCId()) {

                        // Encode if load and store form an exclusive pair
                        BoolExpr isPair = exclPair(load, store, ctx);
                        BoolExpr isExecPair = ctx.mkAnd(isPair, store.exec());
                        enc = ctx.mkAnd(enc, ctx.mkEq(isPair, pairingCond(thread, load, store)));

                        // If load and store have the same address
                        BoolExpr sameAddress = ctx.mkEq(((MemEvent)load).getMemAddressExpr(), (((MemEvent)store).getMemAddressExpr()));
                        unpredictable = ctx.mkOr(unpredictable, ctx.mkAnd(isExecPair, ctx.mkNot(sameAddress)));

                        // Relation between exclusive load and store
                        enc = ctx.mkAnd(enc, ctx.mkEq(edge("rmw", load, store, ctx), ctx.mkAnd(isExecPair, sameAddress)));

                        // Can be executed if addresses mismatch, but behaviour is "constrained unpredictable"
                        // The implementation does not include all possible unpredictable cases: in case of address
                        // mismatch, addresses of read and write are unknown, i.e. read and write can use any address
                        storeExec = ctx.mkOr(storeExec, isPair);
                    }
                }
                enc = ctx.mkAnd(enc, ctx.mkImplies(store.exec(), storeExec));
            }
        }
        return ctx.mkAnd(enc, ctx.mkEq(Flag.ARM_UNPREDICTABLE_BEHAVIOUR.repr(ctx), unpredictable));
    }

    private BoolExpr pairingCond(Thread thread, Event load, Event store){
        BoolExpr pairingCond = ctx.mkAnd(load.exec(), store.exec());

        for (Event otherLoad : thread.getCache().getEvents(loadFilter)) {
            if (otherLoad.getCId() > load.getCId() && otherLoad.getCId() < store.getCId()) {
                pairingCond = ctx.mkAnd(pairingCond, ctx.mkNot(otherLoad.exec()));
            }
        }
        for (Event otherStore : thread.getCache().getEvents(storeFilter)) {
            if (otherStore.getCId() > load.getCId() && otherStore.getCId() < store.getCId()) {
                pairingCond = ctx.mkAnd(pairingCond, ctx.mkNot(otherStore.exec()));
            }
        }
        return pairingCond;
    }

    private BoolExpr exclPair(Event load, Event store, Context ctx){
        return ctx.mkBoolConst("excl(" + load.getCId() + "," + store.getCId() + ")");
    }
}
