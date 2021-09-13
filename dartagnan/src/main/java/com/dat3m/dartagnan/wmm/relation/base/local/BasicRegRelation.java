package com.dat3m.dartagnan.wmm.relation.base.local;

import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.CondJump;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.wmm.relation.base.stat.StaticRelation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.FormulaManager;
import org.sosy_lab.java_smt.api.SolverContext;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.dat3m.dartagnan.program.utils.Utils.generalEqual;

abstract class BasicRegRelation extends StaticRelation {

	abstract Collection<Register> getRegisters(Event regReader);

	abstract Collection<Event> getEvents();

	@Override
	public TupleSet getMinTupleSet() {
		if(null==minTupleSet)
			mkTupleSets();
		return minTupleSet;
	}

	@Override
	public TupleSet getMaxTupleSet(){
		if(null==maxTupleSet)
			mkTupleSets();
		return maxTupleSet;
	}

	@Override
	public BooleanFormula encodeApprox(SolverContext ctx) {
		FormulaManager fmgr = ctx.getFormulaManager();
		BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();

    	BooleanFormula enc = bmgr.makeTrue();

        ImmutableMap<Register, ImmutableList<Event>> regWriterMap = task.getProgram().getCache().getRegWriterMap();

		for (Event regReader : getEvents()) {
            Set<Tuple> writerReaders = maxTupleSet.getBySecond(regReader);
            for (Register register : getRegisters(regReader)) {
                List<Event> writers = regWriterMap.getOrDefault(register, ImmutableList.of());
                List<Event> possibleWriters = writers.stream()
                        .filter(x -> writerReaders.contains(new Tuple(x, regReader)))
                        .collect(Collectors.toList());

                if(writers.isEmpty() || writers.get(0).getCId() >= regReader.getCId()){
                	BooleanFormula equal = generalEqual(register.toIntFormula(regReader, ctx),
                										new IConst(BigInteger.ZERO, register.getPrecision()).toIntFormula(ctx), ctx);
                    enc = bmgr.and(enc, equal);
                } else {

                    for (int i = 0; i < possibleWriters.size(); i++) {
                        Event regWriter = possibleWriters.get(i);
                        // RegReader uses the value of RegWriter if it is executed ..
                        BooleanFormula clause = getExecPair(regWriter, regReader, ctx);
                        BooleanFormula edge = this.getSMTVar(regWriter, regReader, ctx);
                        // .. and no other write to the same register is executed in between
                        for (int j = i + 1; j < possibleWriters.size(); j++) {
                            clause = bmgr.and(clause, bmgr.not(possibleWriters.get(j).exec()));
                        }

                        // Encode edge and value binding
                        enc = bmgr.and(enc, bmgr.equivalence(edge, clause));
                        BooleanFormula equal = generalEqual(((RegWriter) regWriter).getResultRegisterExpr(),
                        									register.toIntFormula(regReader, ctx), ctx);
                        enc = bmgr.and(enc, bmgr.implication(edge, equal));
                    }
                }
            }
        }
        return enc;
    }

	private void mkTupleSets() {
		maxTupleSet = new TupleSet();
		minTupleSet = new TupleSet();
		var eq = task.getBranchEquivalence();
		for(var t : task.getProgram().getThreads()) {
			var s = new HashSet<RegWriter>();
			var j = new TreeMap<Integer,ArrayList<RegWriter>>();
			//ASSERT no backward jump after unrolling
			for(var e : t.getEvents()) {
				//branching
				if(j.firstKey() == e.getCId())
					s.addAll(j.pollFirstEntry().getValue());
				if(e instanceof CondJump) {
					j.computeIfAbsent(((CondJump)e).getLabel().getCId(), k->new ArrayList<>()).addAll(s);
					if(((CondJump)e).isGoto())
						s.clear();
				}
				//write state
				if(e instanceof RegWriter) {
					if(e.cfImpliesExec()) {
						var r = ((RegWriter)e).getResultRegister();
						s.removeIf(x -> x.getResultRegister().equals(r));
					}
					s.add((RegWriter)e);
				}
				//read state
				for(var r : getRegisters(e)) {
					var w = s.stream()
					.filter(x -> x.getResultRegister().equals(r))
					.map(Event.class::cast)
					.collect(Collectors.toList());
					w.forEach(x -> maxTupleSet.add(new Tuple(x, e)));
					w.stream()
					.filter(x -> w.stream()
						.filter(y -> x.getCId() < y.getCId())
						.allMatch(y -> eq.areMutuallyExclusive(x, y)))
					.forEach(x -> minTupleSet.add(new Tuple(x, e)));
				}
			}
			assert j.isEmpty();
		}
	}
}