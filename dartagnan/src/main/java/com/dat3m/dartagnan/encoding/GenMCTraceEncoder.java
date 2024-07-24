package com.dat3m.dartagnan.encoding;

import com.dat3m.dartagnan.expression.type.IntegerType;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.analysis.BranchEquivalence;
import com.dat3m.dartagnan.program.analysis.Dependency;
import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.memory.Memory;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.program.misc.NonDetValue;
import com.dat3m.dartagnan.solver.caat.domain.Domain;
import com.dat3m.dartagnan.verification.model.EventData;
import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.*;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dat3m.dartagnan.configuration.OptionNames.INITIALIZE_REGISTERS;

@Options
public class GenMCTraceEncoder implements Encoder {

    private static final Logger logger = LogManager.getLogger(ProgramEncoder.class);

    // =========================== Configurables ===========================

    @Option(name = INITIALIZE_REGISTERS,
            description = "Assume thread-local variables start off containing zero.",
            secure = true)
    private boolean initializeRegisters = false;

    // =====================================================================

    private final EncodingContext context;
    private final ExecutionAnalysis exec;
    private final Dependency dep;

    private GenMCTraceEncoder(EncodingContext c) {
        Preconditions.checkArgument(c.getTask().getProgram().isCompiled(), "The program must be compiled before encoding.");
        context = c;
        c.getAnalysisContext().requires(BranchEquivalence.class);
        this.exec = c.getAnalysisContext().requires(ExecutionAnalysis.class);
        this.dep = c.getAnalysisContext().requires(Dependency.class);
    }

    public static GenMCTraceEncoder withContext(EncodingContext context) throws InvalidConfigurationException {
        GenMCTraceEncoder encoder = new GenMCTraceEncoder(context);
        context.getTask().getConfig().inject(encoder);
        logger.info("{}: {}", INITIALIZE_REGISTERS, encoder.initializeRegisters);
        return encoder;
    }

    // ============================== Encoding ==============================

    public BooleanFormula encodeFullTrace(Domain<EventData> trace) {
        return context.getBooleanFormulaManager().and(
                encodeConstants(),
                encodeMemory(),
                encodeControlFlow(trace),
                encodeFinalRegisterValues(),
                encodeFilter(),
                encodeDependencies(trace));
    }

    public BooleanFormula encodeConstants() {
        List<BooleanFormula> enc = new ArrayList<>();
        for (NonDetValue value : context.getTask().getProgram().getConstants()) {
            final Formula formula = context.encodeFinalExpression(value);
            if (formula instanceof IntegerFormula intFormula && value.getType() instanceof IntegerType intType) {
                // This special case is for when we encode BVs with integers.
                final IntegerFormulaManager imgr = context.getFormulaManager().getIntegerFormulaManager();
                final IntegerFormula min = imgr.makeNumber(intType.getMinimumValue(value.isSigned()));
                final IntegerFormula max = imgr.makeNumber(intType.getMaximumValue(value.isSigned()));
                enc.add(imgr.greaterOrEquals(intFormula, min));
                enc.add(imgr.lessOrEquals(intFormula, max));
            }
        }
        return context.getBooleanFormulaManager().and(enc);
    }

    public BooleanFormula encodeControlFlow(Domain<EventData> trace) {
        logger.info("Encoding program control flow");

        BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        List<BooleanFormula> enc = new ArrayList<>();
        trace.getElements().stream()
                .filter(evt -> evt.getEvent() instanceof ThreadStart)
                .forEach(evt -> enc.add(encodeThreadCF(evt, trace)));
        return bmgr.and(enc);
    }

    private BooleanFormula encodeThreadCF(EventData threadStart, Domain<EventData> trace) {
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        assert threadStart.getEvent() instanceof ThreadStart;
        final ThreadStart startEvent = (ThreadStart) threadStart.getEvent();
        final List<BooleanFormula> enc = new ArrayList<>();

        final BooleanFormula cfStart = context.controlFlow(startEvent);
        if (startEvent.getCreator() == null) {
            enc.add(cfStart);
        } else if (startEvent.mayFailSpuriously()) {
            enc.add(bmgr.implication(cfStart, context.execution(startEvent.getCreator())));
        } else {
            enc.add(bmgr.equivalence(cfStart, context.execution(startEvent.getCreator())));
        }
        enc.add(startEvent.encodeExec(context));

        Event pred = startEvent;
        int currentId = trace.getId(threadStart) + 1;
        Event e = trace.getObjectById(currentId).getEvent();
        while (startEvent.getThread().equals(e.getThread()) && currentId < trace.size()) {
            e = trace.getObjectById(currentId).getEvent();
            
            // Immediate control flow
            BooleanFormula cfCond = context.controlFlow(pred);
            if (pred instanceof CondJump jump) {
                cfCond = bmgr.and(cfCond, bmgr.not(context.jumpCondition(jump)));
            }

            // Control flow via jumps
            if (e instanceof Label label) {
                for (CondJump jump : label.getJumpSet()) {
                    cfCond = bmgr.or(cfCond, bmgr.and(context.controlFlow(jump), context.jumpCondition(jump)));
                }
            }
            enc.add(bmgr.equivalence(context.controlFlow(e), cfCond));
            enc.add(e.encodeExec(context));
            pred = e;
            ++currentId;
        }
        return bmgr.and(enc);
    }

    // Encodes the address values of memory objects.
    public BooleanFormula encodeMemory() {
        logger.info("Encoding memory");
        final Memory memory = context.getTask().getProgram().getMemory();
        final FormulaManager fmgr = context.getFormulaManager();
        // TODO: Once we want to encode dynamic memory layouts (e.g., due to dynamically-sized mallocs)
        //  we need to compute a Map<MemoryObject, Formula> where each formula describes a
        //  unique, properly aligned, and non-overlapping address of the memory object.
        final Map<MemoryObject, BigInteger> memObj2Addr = computeStaticMemoryLayout(memory);

        final var enc = new ArrayList<BooleanFormula>();
        for (final MemoryObject memObj : memory.getObjects()) {
            // For all objects, their 'final' value fetched here represents their constant value.
            final Formula addressVariable = context.encodeFinalExpression(memObj);
            final BigInteger addressInteger = memObj2Addr.get(memObj);

            if (addressVariable instanceof BitvectorFormula bitvectorVariable) {
                final BitvectorFormulaManager bvmgr = fmgr.getBitvectorFormulaManager();
                final int length = bvmgr.getLength(bitvectorVariable);
                enc.add(bvmgr.equal(bitvectorVariable, bvmgr.makeBitvector(length, addressInteger)));
            } else {
                assert addressVariable instanceof IntegerFormula;
                final IntegerFormulaManager imgr = fmgr.getIntegerFormulaManager();
                enc.add(imgr.equal((IntegerFormula) addressVariable, imgr.makeNumber(addressInteger)));
            }
        }
        return fmgr.getBooleanFormulaManager().and(enc);
    }

    /*
        Computes a static memory layout, i.e., a mapping from memory objects to fixed addresses.
     */
    private Map<MemoryObject, BigInteger> computeStaticMemoryLayout(Memory memory) {
        // Addresses are typically at least two byte aligned
        //      https://stackoverflow.com/questions/23315939/why-2-lsbs-of-32-bit-arm-instruction-address-not-used
        // Many algorithms rely on this assumption for correctness.
        // Many objects have even stricter alignment requirements and need up to 8-byte alignment.
        final BigInteger alignment = BigInteger.valueOf(8);

        Map<MemoryObject, BigInteger> memObj2Addr = new HashMap<>();
        BigInteger nextAddr = alignment;
        for (MemoryObject memObj : memory.getObjects()) {
            memObj2Addr.put(memObj, nextAddr);

            // Compute next aligned address as follows:
            //  nextAddr = curAddr + size + padding = k*alignment   // Alignment requirement
            //  => padding = k*alignment - curAddr - size
            //  => padding mod alignment = (-size) mod alignment    // k*alignment and curAddr are 0 mod alignment.
            //  => padding = (-size) mod alignment                  // Because padding < alignment
            final BigInteger memObjSize = BigInteger.valueOf(memObj.size());
            final BigInteger padding = memObjSize.negate().mod(alignment);
            nextAddr = nextAddr.add(memObjSize).add(padding);
        }

        return memObj2Addr;
    }

    /**
     * @return
     * Describes that for each pair of events, if the reader uses the result of the writer,
     * then the value the reader gets from the register is exactly the value that the writer computed.
     * Also, the reader may only use the value of the latest writer that is executed.
     * Also, if no fitting writer is executed, the reader uses 0.
     */
    public BooleanFormula encodeDependencies(Domain<EventData> trace) {
        logger.info("Encoding dependencies");
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        List<BooleanFormula> enc = new ArrayList<>();

        Collection<Map.Entry<Event,Map<Register, Dependency.State>>> deps = dep.getAll();
        Map<Event, Map<Register, Dependency.State>> depMap = new HashMap<>(deps.size());
        for (Map.Entry<Event,Map<Register, Dependency.State>> e : deps) {
            depMap.put(e.getKey(), e.getValue());
        }
        for (EventData eventData : trace.getElements()) {
            EventData readFromData = eventData.getReadFrom();
            if (readFromData != null) {
                Event readFrom = readFromData.getEvent();
                Event event = eventData.getEvent();
                Register register = null;
                for (Map.Entry<Register, Dependency.State> r : depMap.get(event).entrySet()) {
                    if (r.getValue().may.contains(readFrom)) {
                        register = r.getKey();
                    }
                }
                if (register != null) {
                    enc.add(context.equal(context.encodeExpressionAt(register, event), context.result((RegWriter) readFrom)));
                }
            }
        }
        return bmgr.and(enc);
    }

    public BooleanFormula encodeFilter() {
        return context.getTask().getProgram().getFilterSpecification() != null ?
                context.getTask().getProgram().getFilterSpecification().encode(context) :
                context.getBooleanFormulaManager().makeTrue();
    }
    
    public BooleanFormula encodeFinalRegisterValues() {
        final BooleanFormulaManager bmgr = context.getFormulaManager().getBooleanFormulaManager();
        if (context.getTask().getProgram().getFormat() != Program.SourceLanguage.LITMUS) {
            // LLVM code does not have assertions over final register values, so we do not need to encode them.
            logger.info("Skipping encoding of final register values: C-Code has no assertions over those values.");
            return bmgr.makeTrue();
        }

        logger.info("Encoding final register values");
        List<BooleanFormula> enc = new ArrayList<>();
        for (Map.Entry<Register,Dependency.State> e : dep.finalWriters().entrySet()) {
            final Formula value = context.encodeFinalExpression(e.getKey());
            final Dependency.State state = e.getValue();
            final List<RegWriter> writers = state.may;
            if (initializeRegisters && !state.initialized) {
                List<BooleanFormula> clause = new ArrayList<>();
                clause.add(context.equalZero(value));
                for (Event w : writers) {
                    clause.add(context.execution(w));
                }
                enc.add(bmgr.or(clause));
            }
            for (int i = 0; i < writers.size(); i++) {
                final RegWriter writer = writers.get(i);
                List<BooleanFormula> clause = new ArrayList<>();
                clause.add(context.equal(value, context.result(writer)));
                clause.add(bmgr.not(context.execution(writer)));
                for (Event w : writers.subList(i + 1, writers.size())) {
                    if (!exec.areMutuallyExclusive(writer, w)) {
                        clause.add(context.execution(w));
                    }
                }
                enc.add(bmgr.or(clause));
            }
        }
        return bmgr.and(enc);
    }
}
