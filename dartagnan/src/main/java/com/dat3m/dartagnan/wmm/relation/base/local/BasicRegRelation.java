package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.ImmutableList;
import com.microsoft.z3.BoolExpr;

import java.util.*;
import java.util.stream.Collectors;

abstract class BasicRegRelation <T> extends StaticRelation {

	protected abstract Class<T> filter();

	protected abstract Collection<Register> getRegisters(T regReader);

	@Override
	public TupleSet getMaxTupleSet() {
		if(maxTupleSet == null) {
			maxTupleSet = new TupleSet();
			Map<Register,List<RegWriter>> regWriterMap = program.getCache().getEvents(RegWriter.class).stream().collect(Collectors.groupingBy(RegWriter::getResultRegister));
			for(T o : program.getCache().getEvents(filter())) {
				Event regReader = (Event) o;
				for(Register register : getRegisters(o)) {
					for(RegWriter regWriter : regWriterMap.getOrDefault(register, ImmutableList.of())) {
						Event e = (Event) regWriter;
						if(e.getCId() >= regReader.getCId()) {
							break;
						}
						maxTupleSet.add(new Tuple(e, regReader));
					}
				}
			}
		}
		return maxTupleSet;
	}

	@Override
	protected BoolExpr encodeApprox() {
		BoolExpr enc = ctx.mkTrue();
		Map<Register,List<RegWriter>> regWriterMap = program.getCache().getEvents(RegWriter.class).stream().collect(Collectors.groupingBy(RegWriter::getResultRegister));

		for(T o : program.getCache().getEvents(filter())) {
			Event regReader = (Event) o;
			for(Register register : getRegisters(o)) {
				List<RegWriter> writers = regWriterMap.getOrDefault(register, ImmutableList.of());
				if(writers.isEmpty() || ((Event) writers.get(0)).getCId() >= regReader.getCId()) {
					enc = ctx.mkAnd(enc, ctx.mkEq(register.toZ3Int(regReader, ctx), new IConst(0, register.getPrecision()).toZ3Int(ctx)));

				} else {
					ListIterator<RegWriter> writerIt = writers.listIterator();
					while(writerIt.hasNext()) {
						Event regWriter = (Event)writerIt.next();
						if(regWriter.getCId() >= regReader.getCId()) {
							break;
						}

						// RegReader uses the value of RegWriter if it is executed ..
						BoolExpr clause = ctx.mkAnd(regWriter.exec(), regReader.exec());
						BoolExpr edge = Utils.edge(this.getName(), regWriter, regReader, ctx);

						// .. and no other write to the same register is executed in between
						ListIterator<RegWriter> otherIt = writers.listIterator(writerIt.nextIndex());
						while(otherIt.hasNext()) {
							Event other = (Event) otherIt.next();
							if(other.getCId() >= regReader.getCId()) {
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
