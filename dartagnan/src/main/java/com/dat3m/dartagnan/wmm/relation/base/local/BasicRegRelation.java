package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.microsoft.z3.BoolExpr;
import java.util.*;

abstract class BasicRegRelation extends Relation {

    abstract Collection<Event> getEvents();

    abstract Collection<Register> getRegisters(Event regReader);

    @Override
    public TupleSet getMinTupleSet() {
        if(null == minTupleSet) {
            minTupleSet = new TupleSet();
            for(Event r : getEvents()) {
                for(Register register : getRegisters(r)) {
                    Event latest = null;
                    for(Event w : program.getCache().getRegWriterMap().getOrDefault(register, ImmutableList.of())) {
                        if(r.getCId() <= w.getCId())
                            break;
                        latest = w;
                    }
                    if(null != latest)
                        minTupleSet.add(new Tuple(latest, r));
                }
            }
        }
        return minTupleSet;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null) {
            maxTupleSet = new TupleSet();
            ImmutableMap<Register, ImmutableList<Event>> regWriterMap = program.getCache().getRegWriterMap();
            for(Event regReader : getEvents()){
                for(Register register : getRegisters(regReader)){
                    for(Event regWriter : regWriterMap.getOrDefault(register, ImmutableList.of())){
                        if(regWriter.getCId() >= regReader.getCId()){
                            break;
                        }
                        maxTupleSet.add(new Tuple(regWriter, regReader));
                    }
                }
            }
        }
        return maxTupleSet;
    }

    @Override
    protected BoolExpr encodeApprox() {
        BoolExpr enc = ctx.mkTrue();
        ImmutableMap<Register, ImmutableList<Event>> regWriterMap = program.getCache().getRegWriterMap();

        for (Event regReader : getEvents()) {
            for (Register register : getRegisters(regReader)) {
                List<Event> writers = regWriterMap.getOrDefault(register, ImmutableList.of());
                if(writers.isEmpty() || writers.get(0).getCId() >= regReader.getCId()){
                    enc = ctx.mkAnd(enc, ctx.mkEq(register.toZ3Int(regReader, ctx), new IConst(0, register.getPrecision()).toZ3Int(ctx)));

                } else {
                    ListIterator<Event> writerIt = writers.listIterator();
                    while (writerIt.hasNext()) {
                        Event regWriter = writerIt.next();
                        if (regWriter.getCId() >= regReader.getCId()) {
                            break;
                        }

                        // RegReader uses the value of RegWriter if it is executed ..
                        BoolExpr clause = ctx.mkAnd(regWriter.exec(), regReader.exec());
                        BoolExpr edge = Utils.edge(this.getName(), regWriter, regReader, ctx);

                        // .. and no other write to the same register is executed in between
                        ListIterator<Event> otherIt = writers.listIterator(writerIt.nextIndex());
                        while (otherIt.hasNext()) {
                            Event other = otherIt.next();
                            if (other.getCId() >= regReader.getCId()) {
                                break;
                            }
                            clause = ctx.mkAnd(clause, ctx.mkNot(other.exec()));
                        }

                        // Encode edge and value binding
                        enc = ctx.mkAnd(enc, ctx.mkEq(edge, clause));
                        enc = ctx.mkAnd(enc, ctx.mkImplies(edge, ctx.mkEq(
                                ((RegWriter) regWriter).getResultRegisterExpr(),
                                register.toZ3Int(regReader, ctx)
                        )));
                    }
                }
            }
        }
        return enc;
    }
}
