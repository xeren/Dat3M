package com.dat3m.dartagnan.encoding;

import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.processing.ExprSimplifier;
import com.dat3m.dartagnan.expression.type.IntegerType;
import com.dat3m.dartagnan.expression.type.TypeFactory;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.analysis.BranchEquivalence;
import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.analysis.ReachingDefinitionsAnalysis;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.RegReader;
import com.dat3m.dartagnan.program.event.RegWriter;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.CondJump;
import com.dat3m.dartagnan.program.event.core.MemoryCoreEvent;
import com.dat3m.dartagnan.program.event.core.threading.ThreadJoin;
import com.dat3m.dartagnan.program.event.core.threading.ThreadReturn;
import com.dat3m.dartagnan.program.event.core.threading.ThreadStart;
import com.dat3m.dartagnan.program.memory.Memory;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import com.dat3m.dartagnan.program.misc.NonDetValue;
import com.dat3m.dartagnan.solver.caat4wmm.GenMCEventDomain;
import com.dat3m.dartagnan.utils.Timer;
import com.dat3m.dartagnan.verification.model.EventData;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.*;

import java.math.BigInteger;
import java.util.*;
import java.util.function.BiFunction;

import static com.dat3m.dartagnan.configuration.OptionNames.IGNORE_FILTER_SPECIFICATION;
import static com.dat3m.dartagnan.configuration.OptionNames.INITIALIZE_REGISTERS;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.CO;
import static com.dat3m.dartagnan.wmm.RelationNameRepository.RF;
import static com.google.common.collect.Lists.reverse;

@Options
public class GenMCTraceEncoder implements Encoder {

    private static final Logger logger = LogManager.getLogger(GenMCTraceEncoder.class);
    private static final ExpressionFactory exprs = ExpressionFactory.getInstance();

    // =========================== Configurables ===========================

    @Option(name = INITIALIZE_REGISTERS,
            description = "Assume thread-local variables start off containing zero.",
            secure = true)
    private boolean initializeRegisters = false;

    @Option(name = IGNORE_FILTER_SPECIFICATION,
            description = "Ignore final states filter",
            secure = true)
    private boolean ignoreFilterSpec = false;

    // =====================================================================

    private final EncodingContext context;
    private final ExecutionAnalysis exec;
    private final ReachingDefinitionsAnalysis definitions;

    private final Timer encodingTimer = new Timer();

    private BooleanFormula constants;
    private BooleanFormula memory;
    private BooleanFormula finalRegisterValues;
    private BooleanFormula filter;

    private boolean initialized = false;

    private GenMCTraceEncoder(EncodingContext c) {
        Preconditions.checkArgument(c.getTask().getProgram().isCompiled(), "The program must be compiled before encoding.");
        context = c;
        c.getAnalysisContext().requires(BranchEquivalence.class);
        this.exec = c.getAnalysisContext().requires(ExecutionAnalysis.class);
        this.definitions = c.getAnalysisContext().requires(ReachingDefinitionsAnalysis.class);
    }

    public static GenMCTraceEncoder withContext(EncodingContext context) throws InvalidConfigurationException {
        GenMCTraceEncoder encoder = new GenMCTraceEncoder(context);
        context.getTask().getConfig().inject(encoder);
        logger.info("{}: {}", INITIALIZE_REGISTERS, encoder.initializeRegisters);
        return encoder;
    }

    // ============================== Encoding ==============================

    private void init() {
        if (!initialized) {
            logger.info("Starting encoding");
            encodingTimer.measure(() -> {
                constants = encodeConstants();
                memory = encodeMemory();
                finalRegisterValues = encodeFinalRegisterValues();
                filter = encodeFilter();
            });
            initialized = true;
        }
    }

    public BooleanFormula encodeFullTrace(GenMCEventDomain trace) {
        init();
        return encodingTimer.measure(() -> context.getBooleanFormulaManager().and(
                constants,
                memory,
                encodeControlFlow(trace),
                encodeThreadJoining(trace),
                encodeDependencies(trace)
        ));
    }

    public BooleanFormula encodeConstants() {
        List<BooleanFormula> enc = new ArrayList<>();
        final ExpressionEncoder exprEnc = context.getExpressionEncoder();
        final ExpressionFactory exprs = context.getExpressionFactory();
        for (NonDetValue value : context.getTask().getProgram().getConstants()) {
            if (context.useIntegers && value.getType() instanceof IntegerType intType) {
                // This special case is for when we encode BVs with integers.
                final Expression min = exprs.makeValue(intType.getMinimumValue(value.isSigned()), intType);
                final Expression max = exprs.makeValue(intType.getMaximumValue(value.isSigned()), intType);
                final Expression constraints = exprs.makeAnd(
                        exprs.makeGTE(value, min, value.isSigned()),
                        exprs.makeLTE(value, max, value.isSigned())
                );
                enc.add(exprEnc.encodeBooleanFinal(constraints).formula());
            }
        }
        return context.getBooleanFormulaManager().and(enc);
    }

    public BooleanFormula encodeControlFlow(GenMCEventDomain trace) {
        logger.debug("Encoding program control flow");

        BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        List<BooleanFormula> enc = new ArrayList<>();
        trace.getThreadEventsMap().forEach((thread, events) ->
                enc.add(events.isEmpty() ? bmgr.makeTrue() : encodeThreadCF(thread, trace))
        );
        return bmgr.and(enc);
    }

    private BooleanFormula encodeThreadCF(Thread thread, GenMCEventDomain trace) {
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        final ThreadStart startEvent = thread.getEntry();
        final List<BooleanFormula> enc = new ArrayList<>();

        enc.add(threadIsEnabled(thread));
        enc.add(startEvent.encodeExec(context));

        Collection<Event> traceEvents = trace.getEventMap().keySet();
        for (Event e : startEvent.getSuccessor().getSuccessors()) {
            if (traceEvents.contains(e)) {
                // encode execution semantics
                enc.add(e.encodeExec(context));
                enc.add(bmgr.equivalence(context.controlFlow(e), bmgr.makeTrue()));
                if (e instanceof CondJump jump) {
                    BooleanFormula jumpCond = context.jumpTaken(jump);
                    enc.add(trace.getData(e).orElseThrow().wasExecuted() ? jumpCond : bmgr.not(jumpCond));
                }
            } else {
                enc.add(bmgr.equivalence(context.controlFlow(e), bmgr.makeFalse()));
            }
        }
        return bmgr.and(enc);
    }

    /*
        A thread is enabled if it has no creator or the corresponding ThreadCreate
        event was executed (and didn't fail spuriously).
        // TODO: We could make ThreadCreate "not executed" if it fails rather than guessing the success state here.
        // FIXME: The guessing allows for mismatches: the spawning may succeed but the guess says it doesn't.
     */
    private BooleanFormula threadIsEnabled(Thread thread) {
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        final ThreadStart start = thread.getEntry();
        if (!start.isSpawned() || !start.mayFailSpuriously()) {
            return bmgr.makeTrue();
        } else {
            final String spawnSuccessVarName = "__spawnSuccess#" + thread.getId();
            return bmgr.and(context.execution(start.getCreator()), bmgr.makeVariable(spawnSuccessVarName));
        }
    }

    private BooleanFormula encodeThreadJoining(GenMCEventDomain trace) {
        final Program program = context.getTask().getProgram();
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        final ExpressionEncoder exprEnc = context.getExpressionEncoder();

        List<BooleanFormula> enc = new ArrayList<>();
        for (ThreadJoin join : program.getThreadEvents(ThreadJoin.class)) {
            final boolean joinCf = trace.getData(join).isPresent();
            final boolean terminated = threadHasTerminatedNormally(join.getJoinThread(), trace);
            final boolean joinExec = terminated && joinCf;

            final List<ThreadReturn> returns = join.getJoinThread().getEvents(ThreadReturn.class);
            Verify.verify(returns.size() <= 1, "Unexpected number of ThreadReturn events.");
            // NOTE: No ThreadReturn is currently allowed, if the ThreadReturn event is unreachable and thus is deletable.
            // In this case, the thread never terminates properly, so we do not encode anything.
            // TODO: We might want to make ThreadReturn non-deletable, at least the one we generate in ThreadCreation?
            if (returns.size() == 1 && returns.get(0).hasValue()) {
                final ThreadReturn ret = returns.get(0);
                if (joinExec) {
                    enc.add(exprEnc.equalAt(context.result(join), join, ret.getValue().get(), ret));
                }
                // FIXME: here we assume that proper thread termination implies that ThreadReturn was executed.
                //  While this should be true, we currently do not explicitly checks for this, so the code
                //  is a little dangerous.
                //  It would probably good to give each thread a single ThreadReturn event that is executed
                //   IFF the thread terminates properly.
            }
        }

        return bmgr.and(enc);
    }

    private boolean threadHasTerminatedNormally(Thread thread, GenMCEventDomain trace) {
        final boolean exception = thread.getEvents(CondJump.class).stream()
                .filter(jump -> jump.hasTag(Tag.EXCEPTIONAL_TERMINATION))
                .anyMatch(jump -> trace.getData(jump).map(EventData::wasExecuted).orElse(false));
        return threadHasTerminated(thread, trace) && !exception;
    }

    // NOTE: A thread that was never spawned is also non-terminating.
    private boolean threadHasTerminated(Thread thread, GenMCEventDomain trace) {
        return trace.getData(thread.getExit()).isPresent() // Also guarantees that we are not stuck in a barrier
                && !threadIsStuckInLoop(thread, trace);
    }

    // NOTE: Stuckness also considers bound events, i.e., insufficiently unrolled loops.
    private boolean threadIsStuckInLoop(Thread thread, GenMCEventDomain trace) {
        return thread.getEvents(CondJump.class).stream()
                .filter(jump -> jump.hasTag(Tag.NONTERMINATION))
                .anyMatch(jump -> trace.getData(jump).map(EventData::wasExecuted).orElse(false));
    }

    // ====================================== Memory layout ======================================

    // Encodes the address values and sizes of memory objects.
    public BooleanFormula encodeMemory() {
        logger.info("Encoding memory");
        return encodeMemoryLayout(context.getTask().getProgram().getMemory());
    }

    private BooleanFormula encodeMemoryLayout(Memory memory) {
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        final ExpressionEncoder exprEnc = context.getExpressionEncoder();
        final IntegerType archType = TypeFactory.getInstance().getArchType();
        final List<BooleanFormula> enc = new ArrayList<>();

        // TODO: We could sort the objects to generate better encoding:
        //  "static -> dynamic with known size -> dynamic with unknown size"
        //  For the former two we can then statically assign addresses as we do now and
        //  only the latter needs dynamic encodings.
        final List<MemoryObject> memoryObjects = ImmutableList.copyOf(memory.getObjects());
        for (int i = 0; i < memoryObjects.size(); i++) {
            final MemoryObject cur = memoryObjects.get(i);
            final Expression addrVar = context.address(cur);
            final Expression sizeVar = context.size(cur);

            final Expression size;
            final Expression alignment;
            // Compute size & alignment expressions
            if (cur.isStaticallyAllocated()) {
                size = cur.size();
                alignment = cur.alignment();
            } else {
                final Expression exec = exprEnc.wrap(context.execution(cur.getAllocationSite()));
                final Expression zero = exprs.makeValue(BigInteger.ZERO, archType);
                final Expression one = exprs.makeValue(BigInteger.ONE, archType);

                // NOTE: If we know the size/alignment of the allocation, we can pre-reserve memory space,
                // even if the allocation does not get executed. This improves performance.
                // We could also do this if the size is unknown but has a known upper bound.
                size = cur.hasKnownSize() ? cur.size() : exprs.makeITE(exec, cur.size(), zero);
                alignment = cur.hasKnownAlignment() ? cur.alignment() : exprs.makeITE(exec, cur.alignment(), one);
            }

            final BiFunction<Expression, Expression, BooleanFormula> equate = (a, b) -> {
                final Event alloc = cur.getAllocationSite();
                return cur.isStaticallyAllocated()
                        ? exprEnc.equal(a, b)
                        : exprEnc.equalAt(a, alloc, b, alloc);
            };

            // Encode size
            enc.add(equate.apply(sizeVar, size));

            // Encode address (we even give non-allocated objects a proper, well-aligned address)
            final MemoryObject prev = i > 0 ? memoryObjects.get(i - 1) : null;
            if (prev == null) {
                // First object is placed at alignment
                enc.add(equate.apply(addrVar, alignment));
            } else {
                final Expression nextAvailableAddr = exprs.makeAdd(context.address(prev), context.size(prev));
                final Expression nextAlignedAddr = exprs.makeAdd(nextAvailableAddr,
                        exprs.makeSub(alignment, exprs.makeRem(nextAvailableAddr, alignment,  true))
                );

                // ... other objects are placed at the next well-aligned address that is available.
                enc.add(equate.apply(addrVar, nextAlignedAddr));
            }
        }

        return bmgr.and(enc);
    }

    private static Map<MemoryObject, Expression> computeMemoryLayout(Memory memory) {
        ExprSimplifier simplifier = new ExprSimplifier(true);
        Map<MemoryObject, Expression> memoryLayout = new HashMap<>();

        // TODO: We could sort the objects to generate better encoding:
        //  "static -> dynamic with known size -> dynamic with unknown size"
        //  For the former two we can then statically assign addresses as we do now and
        //  only the latter needs dynamic encodings.
        MemoryObject prev = null;
        Expression prevSize = null;
        final List<MemoryObject> memoryObjects = ImmutableList.copyOf(memory.getObjects());
        for (final MemoryObject cur : memoryObjects) {
            // Compute size & alignment expressions
            final Expression size = cur.size();
            final Expression alignment = cur.alignment();

            // Encode address (we even give non-allocated objects a proper, well-aligned address)
            if (prev == null) {
                // First object is placed at alignment
                memoryLayout.put(cur, alignment.accept(simplifier));
            } else {
                final Expression nextAvailableAddr = exprs.makeAdd(memoryLayout.get(prev), prevSize);
                final Expression nextAlignedAddr = exprs.makeAdd(nextAvailableAddr,
                        exprs.makeSub(alignment, exprs.makeRem(nextAvailableAddr, alignment, true))
                );

                // ... other objects are placed at the next well-aligned address that is available.
                memoryLayout.put(cur, nextAlignedAddr.accept(simplifier));
            }
            prev = cur;
            prevSize = size;
        }

        return memoryLayout;
    }

    /**
     * @return
     * Describes that for each pair of events, if the reader uses the result of the writer,
     * then the value the reader gets from the register is exactly the value that the writer computed.
     * Also, the reader may only use the value of the latest writer that is executed.
     * Also, if no fitting writer is executed, the reader uses 0.
     */
    public BooleanFormula encodeDependencies(GenMCEventDomain trace) {
        logger.debug("Encoding dependencies");
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        final ExpressionEncoder exprEncoder = context.getExpressionEncoder();
        List<BooleanFormula> enc = new ArrayList<>();

        EncodingContext.EdgeEncoder rf = context.edge(context.getTask().getMemoryModel().getRelation(RF));
        EncodingContext.EdgeEncoder co = context.edge(context.getTask().getMemoryModel().getRelation(CO));
        for (EventData eventData : trace.getElements()) {
            EventData readFromData = eventData.getReadFrom();
            if (eventData.isRead()) {
                if (readFromData != null) {
                    MemoryCoreEvent readFrom = (MemoryCoreEvent) readFromData.getEvent();
                    MemoryCoreEvent event = (MemoryCoreEvent) eventData.getEvent();
    
                    // Execution model
                    enc.add(rf.encode(readFrom, event));
    
                    // Data flow
                    enc.add(context.sameAddress(event, readFrom));
                    enc.add(context.sameValue(event, readFrom));
                }
            } else if (eventData.isWrite() && eventData.getCoSuccessor() != null) {
                MemoryCoreEvent event = (MemoryCoreEvent) eventData.getEvent();
                MemoryCoreEvent coSuccessor = (MemoryCoreEvent) eventData.getCoSuccessor().getEvent();

                enc.add(co.encode(event, coSuccessor));
                enc.add(bmgr.not(co.encode(coSuccessor, event)));   // co is total
                enc.add(context.sameAddress(event, coSuccessor));
            }
        }
        for (RegReader reader : context.getTask().getProgram().getThreadEvents(RegReader.class)) {
            final ReachingDefinitionsAnalysis.Writers writers = definitions.getWriters(reader);
            for (Register register : writers.getUsedRegisters()) {
                final List<BooleanFormula> overwrite = new ArrayList<>();
                final ReachingDefinitionsAnalysis.RegisterWriters reg = writers.ofRegister(register);
                for (RegWriter writer : reverse(reg.getMayWriters())) {
                    BooleanFormula edge;
                    if (reg.getMustWriters().contains(writer)) {
                        if (exec.isImplied(reader, writer) && reader.cfImpliesExec()) {
                            // This special case is important. Usually, we encode "dep => regValue = regWriterResult"
                            // By getting rid of the guard "dep" in this special case, we end up with an unconditional
                            // "regValue = regWriterResult", which allows the solver to eliminate one of the variables
                            // in preprocessing.
                            assert reg.getMayWriters().size() == 1;
                            edge = bmgr.makeTrue();
                        } else {
                            edge = bmgr.and(context.execution(writer), context.controlFlow(reader));
                        }
                    } else {
                        edge = context.dependency(writer, reader);
                        enc.add(bmgr.equivalence(edge, bmgr.and(context.execution(writer), context.controlFlow(reader), bmgr.not(bmgr.or(overwrite)))));
                    }
                    BooleanFormula equalValue = exprEncoder.equalAt(register, reader, context.result(writer), writer);
                    enc.add(bmgr.implication(edge, equalValue));
                    overwrite.add(context.execution(writer));
                }

                if(initializeRegisters && !reg.mustBeInitialized()) {
                    final Expression zero = exprs.makeGeneralZero(register.getType());
                    overwrite.add(bmgr.not(context.controlFlow(reader)));
                    overwrite.add(exprEncoder.equalAt(register, reader, zero, reader));
                    enc.add(bmgr.or(overwrite));
                }
            }
        }
        return bmgr.and(enc);
    }

    public BooleanFormula encodeFilter() {
        final Expression filterSpec = context.getTask().getProgram().getFilterSpecification();
        if (!ignoreFilterSpec && filterSpec != null) {
            return context.getExpressionEncoder().encodeBooleanFinal(filterSpec).formula();
        }
        return context.getBooleanFormulaManager().makeTrue();
    }
    
    public BooleanFormula encodeFinalRegisterValues() {
        final BooleanFormulaManager bmgr = context.getFormulaManager().getBooleanFormulaManager();
        final ExpressionEncoder exprEncoder = context.getExpressionEncoder();
        if (context.getTask().getProgram().getFormat() != Program.SourceLanguage.LITMUS) {
            // LLVM code does not have assertions over final register values, so we do not need to encode them.
            logger.info("Skipping encoding of final register values: C-Code has no assertions over those values.");
            return bmgr.makeTrue();
        }

        logger.info("Encoding final register values");
        List<BooleanFormula> enc = new ArrayList<>();
        final ReachingDefinitionsAnalysis.Writers finalState = definitions.getFinalWriters();
        for (Register register : finalState.getUsedRegisters()) {
            final ReachingDefinitionsAnalysis.RegisterWriters registerWriters = finalState.ofRegister(register);
            final List<RegWriter> writers = registerWriters.getMayWriters();
            if (initializeRegisters && !registerWriters.mustBeInitialized()) {
                List<BooleanFormula> clause = new ArrayList<>();
                clause.add(exprEncoder.equal(register, exprs.makeGeneralZero(register.getType())));
                for (Event w : writers) {
                    clause.add(context.execution(w));
                }
                enc.add(bmgr.or(clause));
            }
            for (int i = 0; i < writers.size(); i++) {
                final RegWriter writer = writers.get(i);
                List<BooleanFormula> clause = new ArrayList<>();
                clause.add(exprEncoder.equal(register, context.result(writer)));
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

    public BooleanFormula getPropertyRelevantEncoding() {
        init();
        return context.getBooleanFormulaManager().and(finalRegisterValues, filter);
    }

    public static Map<MemoryObject, Expression> getMemoryLayout(Memory memory) {
        return computeMemoryLayout(memory);
    }

    public EncodingContext getEncodingContext() {
        return context;
    }

    public Timer getEncodingTimer() {
        return encodingTimer;
    }
}
