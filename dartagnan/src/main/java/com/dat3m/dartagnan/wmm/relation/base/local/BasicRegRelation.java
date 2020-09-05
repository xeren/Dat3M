package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.microsoft.z3.BoolExpr;
import java.util.*;

abstract class BasicRegRelation extends StaticRelation {

	protected final String etype;

	BasicRegRelation(String etype) {
		this.etype = etype;
	}

	protected abstract Collection<Register> getRegisters(Event regReader);

	protected final void doEncodeApprox(EncodeContext e, Atom atom, Collection<Event> regReaders) {
		ImmutableMap<Register, ImmutableList<Event>> regWriterMap = program.getCache().getRegWriterMap();

		for(Event regReader: regReaders) {
			for(Register register: getRegisters(regReader)) {
				List<Event> writers = regWriterMap.getOrDefault(register, ImmutableList.of());
				if(writers.isEmpty() || writers.get(0).getCId() >= regReader.getCId()) {
					e.rule(e.eq(e.zero(), register.toZ3Int(regReader, e.context)));
					continue;
				}

				ListIterator<Event> writerIt = writers.listIterator();
				while(writerIt.hasNext()) {
					Event regWriter = writerIt.next();
					if(regWriter.getCId() >= regReader.getCId())
						break;

					// RegReader uses the value of RegWriter if it is executed ..
					LinkedList<BoolExpr> clause = new LinkedList<>();
					clause.add(regWriter.exec());
					clause.add(regReader.exec());
					BoolExpr edge = atom.of(regWriter, regReader);

					// .. and no other write to the same register is executed in between
					ListIterator<Event> otherIt = writers.listIterator(writerIt.nextIndex());
					while(otherIt.hasNext()) {
						Event other = otherIt.next();
						if(other.getCId() >= regReader.getCId())
							break;
						clause.add(e.not(other.exec()));
					}

					// Encode edge and value binding
					e.rule(e.eq(edge, e.and(clause)));
					e.rule(e.implies(edge, e.eq(((RegWriter) regWriter).getResultRegisterExpr(), register.toZ3Int(regReader, e.context))));
				}
			}
		}
	}

	@Override
	public TupleSet getMaxTupleSet() {
		if(maxTupleSet == null) {
			maxTupleSet = new TupleSet();
			ImmutableMap<Register, ImmutableList<Event>> regWriterMap = program.getCache().getRegWriterMap();
			for(Event regReader: program.getCache().getEvents(FilterBasic.get(etype))) {
				for(Register register: getRegisters(regReader)) {
					for(Event regWriter: regWriterMap.getOrDefault(register, ImmutableList.of())) {
						if(regWriter.getCId() >= regReader.getCId())
							break;
						maxTupleSet.add(new Tuple(regWriter, regReader));
					}
				}
			}
		}
		return maxTupleSet;
	}

	@Override
	protected void encodeApprox(EncodeContext context, Atom atom) {
		doEncodeApprox(context, atom, program.getCache().getEvents(FilterBasic.get(etype)));
	}
}
