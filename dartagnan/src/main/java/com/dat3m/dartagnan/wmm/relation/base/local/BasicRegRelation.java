package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.microsoft.z3.BoolExpr;
import java.util.*;

abstract class BasicRegRelation extends StaticRelation {

	protected final String etype;

	BasicRegRelation(String etype) {
		this.etype = etype;
	}

	protected abstract Collection<Register> getRegisters(Event regReader);

	protected final void encodeApprox(EncodeContext e, ProgramCache p, Atom atom) {
		for(Event regReader: p.cache(FilterBasic.get(etype))) {
			for(Register register: getRegisters(regReader)) {
				List<RegWriter> writers = p.cache(register);
				if(writers.isEmpty() || ((Event)writers.get(0)).getCId() >= regReader.getCId()) {
					e.rule(e.eq(e.zero(), register.toZ3Int(regReader, e)));
					continue;
				}

				ListIterator<RegWriter> writerIt = writers.listIterator();
				while(writerIt.hasNext()) {
					RegWriter w = writerIt.next();
					Event regWriter = (Event)w;
					if(regWriter.getCId() >= regReader.getCId())
						break;

					// RegReader uses the value of RegWriter if it is executed ..
					LinkedList<BoolExpr> clause = new LinkedList<>();
					clause.add(e.exec(regWriter));
					clause.add(e.exec(regReader));
					BoolExpr edge = atom.of(regWriter, regReader);

					// .. and no other write to the same register is executed in between
					ListIterator<RegWriter> otherIt = writers.listIterator(writerIt.nextIndex());
					while(otherIt.hasNext()) {
						Event other = (Event)otherIt.next();
						if(other.getCId() >= regReader.getCId())
							break;
						clause.add(e.not(e.exec(other)));
					}

					// Encode edge and value binding
					e.rule(e.eq(edge, e.and(clause)));
					e.rule(e.implies(edge, e.eq(w.getResultRegisterExpr(e), register.toZ3Int(regReader, e))));
				}
			}
		}
	}

	@Override
	protected void update(ProgramCache p, TupleSet s) {
		for(Event regReader: p.cache(FilterBasic.get(etype))) {
			for(Register register: getRegisters(regReader)) {
				for(RegWriter regWriter: p.cache(register)) {
					if(((Event)regWriter).getCId() >= regReader.getCId())
						break;
					s.add(new Tuple((Event)regWriter, regReader));
				}
			}
		}
	}
}
