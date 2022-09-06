package com.dat3m.dartagnan.encoding;

import com.dat3m.dartagnan.GlobalSettings;
import com.dat3m.dartagnan.expression.utils.Utils;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.analysis.ExecutionAnalysis;
import com.dat3m.dartagnan.program.analysis.alias.AliasAnalysis;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.program.filter.FilterIntersection;
import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.wmm.Constraint;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.Flag;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.NumeralFormula;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dat3m.dartagnan.configuration.OptionNames.IDL_TO_SAT;
import static com.dat3m.dartagnan.configuration.OptionNames.POST_FIXPOINT;
import static com.dat3m.dartagnan.encoding.ProgramEncoder.execution;
import static com.dat3m.dartagnan.expression.utils.Utils.generalEqual;
import static com.dat3m.dartagnan.program.event.Tag.INIT;
import static com.dat3m.dartagnan.program.event.Tag.WRITE;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.RF;
import static com.dat3m.dartagnan.wmm.utils.Utils.*;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Options
public class WmmEncoder {

    private static final Logger logger = LogManager.getLogger(WmmEncoder.class);

    // =========================== Configurables ===========================

    @Option(
            name=IDL_TO_SAT,
            description = "Use SAT-based encoding for totality and acyclicity.",
            secure = true)
    private boolean useSATEncoding = false;

    @Option(
            name = POST_FIXPOINT,
            description = "Allow relationship variables to be assigned true without reason.",
            secure = true)
    private boolean postFixApprox = false;

    // =====================================================================

    private final Program program;
    private final Wmm memoryModel;
    private final Context analysisContext;
    private final SolverContext ctx;
    private final AxiomEncoder axiomEncoder;

    // =====================================================================

    private WmmEncoder(Program p, Wmm m, Context context, SolverContext solverContext) {
        program = checkNotNull(p);
        memoryModel = checkNotNull(m);
        analysisContext = context;
        context.requires(RelationAnalysis.class);
        context.requires(EncodeSets.class);
        ctx = solverContext;
        axiomEncoder = new AxiomEncoder(context, ctx);
    }

    public static WmmEncoder fromConfig(Program program, Wmm memoryModel, Context context, Configuration config, SolverContext ctx) throws InvalidConfigurationException {
        WmmEncoder encoder = new WmmEncoder(program, memoryModel, context, ctx);
        config.inject(encoder);
        config.inject(encoder.axiomEncoder);
        encoder.initializeEncoding();
        return encoder;
    }

    private void initializeEncoding() {
        for(String relName : Wmm.BASE_RELATIONS) {
            memoryModel.getRelation(relName);
        }
    }

    public BooleanFormula encodeFullMemoryModel() {
        return ctx.getFormulaManager().getBooleanFormulaManager().and(
                encodeRelations(),
                encodeConsistency()
        );
    }

    // Initializes everything just like encodeAnarchicSemantics but also encodes all
    // relations that are needed for the axioms (but does NOT encode the axioms themselves yet)
    // NOTE: It avoids encoding relations that do NOT affect the axioms, i.e. unused relations
    public BooleanFormula encodeRelations() {
        logger.info("Encoding relations");
        final DependencyGraph<Relation> depGraph = DependencyGraph.from(
                Iterables.concat(
                        Iterables.transform(Wmm.BASE_RELATIONS, memoryModel::getRelation), // base relations
                        Iterables.transform(memoryModel.getAxioms(), Axiom::getRelation) // axiom relations
                )
        );
        RelationEncoder v = new RelationEncoder();
        BooleanFormula enc = v.bmgr.makeTrue();
        for (Relation rel : depGraph.getNodeContents()) {
            enc = v.bmgr.and(enc, rel.visit(v));
        }
        return enc;
    }

    // Encodes all axioms. This should be called after <encodeRelations>
    public BooleanFormula encodeConsistency() {
        logger.info("Encoding consistency");
        final BooleanFormulaManager bmgr = ctx.getFormulaManager().getBooleanFormulaManager();
        BooleanFormula enc = bmgr.makeTrue();
        for (Axiom ax : memoryModel.getAxioms()) {
            if (!ax.isFlagged()) {
                enc = bmgr.and(enc, ax.visit(axiomEncoder));
            }
        }
        return enc;
    }

    private final class RelationEncoder implements Constraint.Visitor<BooleanFormula> {
        final ExecutionAnalysis exec = analysisContext.get(ExecutionAnalysis.class);
        final AliasAnalysis alias = analysisContext.get(AliasAnalysis.class);
        final RelationAnalysis ra = analysisContext.get(RelationAnalysis.class);
        final EncodeSets encodeSets = analysisContext.get(EncodeSets.class);
        final IntegerFormulaManager imgr = ctx.getFormulaManager().getIntegerFormulaManager();
        final BooleanFormulaManager bmgr = ctx.getFormulaManager().getBooleanFormulaManager();

        @Override
        public BooleanFormula visitUnion(Relation rel, Relation r1, Relation r2) {
            BooleanFormula enc = bmgr.makeTrue();
            Set<Tuple> may1 = ra.may(r1);
            Set<Tuple> may2 = ra.may(r2);
            for (Tuple tuple : encodeSets.get(rel)) {
                BooleanFormula edge = rel.getSMTVar(tuple, ctx);
                BooleanFormula opt1 = may1.contains(tuple) ? r1.getSMTVar(tuple, ctx) : bmgr.makeFalse();
                BooleanFormula opt2 = may2.contains(tuple) ? r2.getSMTVar(tuple, ctx) : bmgr.makeFalse();
                if (postFixApprox) {
                    enc = bmgr.and(enc, bmgr.implication(bmgr.or(opt1, opt2), edge));
                } else {
                    enc = bmgr.and(enc, bmgr.equivalence(edge, bmgr.or(opt1, opt2)));
                }
            }
            return enc;
        }

        @Override
        public BooleanFormula visitIntersection(Relation rel, Relation r1, Relation r2) {
            BooleanFormula enc = bmgr.makeTrue();
            TupleSet must1 = r1.getMinTupleSet();
            TupleSet must2 = r2.getMinTupleSet();
            for (Tuple tuple : encodeSets.get(rel)) {
                BooleanFormula edge = rel.getSMTVar(tuple, ctx);
                BooleanFormula opt1 = must1.contains(tuple) ? bmgr.makeTrue() : r1.getSMTVar(tuple, ctx);
                BooleanFormula opt2 = must2.contains(tuple) ? bmgr.makeTrue() : r2.getSMTVar(tuple, ctx);
                enc = bmgr.and(enc, bmgr.equivalence(edge, bmgr.and(opt1, opt2)));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitDifference(Relation rel, Relation r1, Relation r2) {
            BooleanFormula enc = bmgr.makeTrue();
            Set<Tuple> must1 = ra.must(r1);
            Set<Tuple> may2 = ra.may(r2);
            for (Tuple tuple : encodeSets.get(rel)) {
                BooleanFormula edge = rel.getSMTVar(tuple, ctx);
                BooleanFormula opt1 = must1.contains(tuple) ? execution(tuple.getFirst(), tuple.getSecond(), exec, ctx) : r1.getSMTVar(tuple, ctx);
                BooleanFormula opt2 = may2.contains(tuple) ? bmgr.not(r2.getSMTVar(tuple, ctx)) : bmgr.makeTrue();
                if (postFixApprox) {
                    enc = bmgr.and(enc, bmgr.implication(bmgr.and(opt1, opt2), edge));
                } else {
                    enc = bmgr.and(enc, bmgr.equivalence(edge, bmgr.and(opt1, opt2)));
                }
            }
            return enc;
        }

        @Override
        public BooleanFormula visitComposition(Relation rel, Relation r1, Relation r2) {
            BooleanFormula enc = bmgr.makeTrue();
            Set<Tuple> set1 = encodeSets.get(r1);
            Set<Tuple> set2 = encodeSets.get(r2);
            TupleSet must1 = r1.getMinTupleSet();
            TupleSet must2 = r2.getMinTupleSet();
            for (Tuple tuple : encodeSets.get(rel)) {
                Event e1 = tuple.getFirst();
                Event e2 = tuple.getSecond();
                BooleanFormula edge = rel.getSMTVar(tuple, ctx);
                BooleanFormula expr = bmgr.makeFalse();
                for (Tuple t1 : must1.getByFirst(e1)) {
                    Event e3 = t1.getSecond();
                    Tuple t2 = new Tuple(e3, e2);
                    if (must2.contains(t2)) {
                        expr = bmgr.or(expr, bmgr.and(e1.exec(), e3.exec(), e2.exec()));
                    } else if (set2.contains(t2)) {
                        expr = bmgr.or(expr, bmgr.and(
                                exec.isImplied(e2, e1) || exec.isImplied(e3, e1) ? bmgr.makeTrue() : e1.exec(),
                                r2.getSMTVar(t2, ctx)));
                    }
                }
                for (Tuple t1 : set1) {
                    Event e3 = t1.getSecond();
                    if (t1.getFirst().getCId() != e1.getCId()) {
                        continue;
                    }
                    Tuple t2 = new Tuple(e3, e2);
                    if (must2.contains(t2)) {
                        expr = bmgr.or(expr, bmgr.and(
                                r1.getSMTVar(t1, ctx),
                                exec.isImplied(e1, e2) || exec.isImplied(e3, e2) ? bmgr.makeTrue() : e2.exec()));
                    } else if (set2.contains(t2)) {
                        expr = bmgr.or(expr, bmgr.and(r1.getSMTVar(t1, ctx), r2.getSMTVar(t2, ctx)));
                    }
                }
                enc = bmgr.and(enc, bmgr.equivalence(edge, expr));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitDomainIdentity(Relation rel, Relation r1) {
            BooleanFormula enc = bmgr.makeTrue();
            TupleSet may1 = ra.may(r1);
            Set<Tuple> must1 = ra.must(r1);
            for (Tuple tuple : encodeSets.get(rel)) {
                BooleanFormula edge = rel.getSMTVar(tuple, ctx);
                Event e = tuple.getFirst();
                BooleanFormula opt = bmgr.makeFalse();
                for (Tuple t : may1.getByFirst(e)) {
                    opt = bmgr.or(opt, must1.contains(t) ? execution(e, t.getSecond(), exec, ctx) : r1.getSMTVar(e, t.getSecond(), ctx));
                }
                enc = bmgr.and(enc, bmgr.equivalence(edge, opt));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitRangeIdentity(Relation rel, Relation r1) {
            BooleanFormula enc = bmgr.makeTrue();
            TupleSet may1 = ra.may(r1);
            Set<Tuple> must1 = ra.must(r1);
            for (Tuple tuple : encodeSets.get(rel)) {
                BooleanFormula edge = rel.getSMTVar(tuple, ctx);
                Event e = tuple.getFirst();
                BooleanFormula opt = bmgr.makeFalse();
                for (Tuple t : may1.getBySecond(e)) {
                    opt = bmgr.or(opt, must1.contains(t) ? execution(t.getFirst(), e, exec, ctx) : r1.getSMTVar(t.getFirst(), e, ctx));
                }
                enc = bmgr.and(enc, bmgr.equivalence(edge, opt));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitTransitiveClosure(Relation rel, Relation r1) {
            BooleanFormula enc = bmgr.makeTrue();
            Set<Tuple> set = encodeSets.get(rel);
            TupleSet must = rel.getMinTupleSet();
            Set<Tuple> set1 = encodeSets.get(r1);
            for (Tuple tuple : set) {
                BooleanFormula edge = rel.getSMTVar(tuple, ctx);
                BooleanFormula orClause = bmgr.makeFalse();
                Event e1 = tuple.getFirst();
                Event e2 = tuple.getSecond();
                if (set1.contains(tuple)) {
                    orClause = bmgr.or(orClause, r1.getSMTVar(tuple, ctx));
                }
                for (Tuple t1 : must.getByFirst(e1)) {
                    Event e3 = t1.getSecond();
                    if (e3.getCId() == e1.getCId() || e3.getCId() == e2.getCId()) {
                        continue;
                    }
                    Tuple t2 = new Tuple(e3, e2);
                    if (must.contains(t2)) {
                        orClause = bmgr.or(orClause, bmgr.and(e1.exec(), e2.exec(), e3.exec()));
                    } else if (set.contains(t2)) {
                        orClause = bmgr.or(orClause, bmgr.and(
                                exec.isImplied(e2, e1) || exec.isImplied(e3, e1) ? bmgr.makeTrue() : e1.exec(),
                                rel.getSMTVar(t2, ctx)));
                    }
                }
                for (Tuple t1 : set) {
                    Event e3 = t1.getSecond();
                    if (t1.getFirst().getCId() != e1.getCId() || e3.getCId() == e1.getCId() || e3.getCId() == e2.getCId()) {
                        continue;
                    }
                    Tuple t2 = new Tuple(e3, e2);
                    if (must.contains(t2)) {
                        orClause = bmgr.or(orClause, bmgr.and(
                                r1.getSMTVar(t1, ctx),
                                exec.isImplied(e1, e2) || exec.isImplied(e3, e2) ? bmgr.makeTrue() : e2.exec()));
                    } else if (set.contains(t2)) {
                        orClause = bmgr.or(orClause, bmgr.and(r1.getSMTVar(t1, ctx), rel.getSMTVar(t2, ctx)));
                    }
                }
                if (postFixApprox) {
                    enc = bmgr.and(enc, bmgr.implication(orClause, edge));
                } else {
                    enc = bmgr.and(enc, bmgr.equivalence(edge, orClause));
                }
            }
            return enc;
        }

        @Override
        public BooleanFormula visitReflexiveTransitiveClosure(Relation rel, Relation r1) {
            return visitTransitiveClosure(rel, r1);
        }

        @Override
        public BooleanFormula visitInverse(Relation rel, Relation r1) {
            BooleanFormula enc = bmgr.makeTrue();
            for (Tuple tuple : encodeSets.get(rel)) {
                enc = bmgr.and(enc, bmgr.equivalence(
                        rel.getSMTVar(tuple, ctx),
                        r1.getSMTVar(tuple.getInverse(), ctx)));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitCompareAndSwapDependency(Relation rel) {
            return bmgr.makeTrue();
        }

        @Override
        public BooleanFormula visitProduct(Relation rel, FilterAbstract domain, FilterAbstract range) {
            return bmgr.makeTrue();
        }

        @Override
        public BooleanFormula visitExternal(Relation rel) {
            return bmgr.makeTrue();
        }

        @Override
        public BooleanFormula visitInternal(Relation rel) {
            return bmgr.makeTrue();
        }

        @Override
        public BooleanFormula visitIdentity(Relation rel, FilterAbstract set) {
            return bmgr.makeTrue();
        }

        @Override
        public BooleanFormula visitProgramOrder(Relation po) {
            return bmgr.makeTrue();
        }

        @Override
        public BooleanFormula visitControl(Relation ctrl) {
            return bmgr.makeTrue();
        }

        @Override
        public BooleanFormula visitFences(Relation rel, FilterAbstract fenceSet) {
            BooleanFormula enc = bmgr.makeTrue();
            List<Event> fences = program.getCache().getEvents(fenceSet);
            for (Tuple tuple : encodeSets.get(rel)) {
                BooleanFormula edge = rel.getSMTVar(tuple, ctx);
                Event e1 = tuple.getFirst();
                Event e2 = tuple.getSecond();
                BooleanFormula orClause = fences.stream()
                        .filter(f -> e1.getCId() < f.getCId() && f.getCId() < e2.getCId())
                        .map(Event::exec).reduce(bmgr.makeFalse(), bmgr::or);
                enc = bmgr.and(enc, bmgr.equivalence(edge, bmgr.and(execution(e1, e2, exec, ctx), orClause)));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitCriticalSections(Relation rscs) {
            BooleanFormula enc = bmgr.makeTrue();
            TupleSet maySet = ra.may(rscs);
            for (Tuple tuple : encodeSets.get(rscs)) {
                Event lock = tuple.getFirst();
                Event unlock = tuple.getSecond();
                BooleanFormula relation = execution(lock, unlock, exec, ctx);
                for (Tuple t : maySet.getBySecond(unlock)) {
                    Event y = t.getFirst();
                    if (lock.getCId() < y.getCId() && y.getCId() < unlock.getCId()) {
                        relation = bmgr.and(relation, bmgr.not(rscs.getSMTVar(t, ctx)));
                    }
                }
                for (Tuple t : maySet.getByFirst(lock)) {
                    Event y = t.getSecond();
                    if (lock.getCId() < y.getCId() && y.getCId() < unlock.getCId()) {
                        relation = bmgr.and(relation, bmgr.not(rscs.getSMTVar(t, ctx)));
                    }
                }
                enc = bmgr.and(enc, bmgr.equivalence(rscs.getSMTVar(tuple, ctx), relation));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitReadModifyWrites(Relation rmw) {
            BooleanFormula enc = bmgr.makeTrue();
            BooleanFormula unpredictable = bmgr.makeFalse();
            TupleSet maySet = ra.may(rmw);
            for (Event store : program.getCache().getEvents(
                    FilterIntersection.get(FilterBasic.get(Tag.WRITE), FilterBasic.get(Tag.EXCL)))) {
                checkState(store instanceof MemEvent, "non-memory event participating in '" + rmw.getName() + "'");
                BooleanFormula storeExec = bmgr.makeFalse();
                for (Tuple t : maySet.getBySecond(store)) {
                    MemEvent load = (MemEvent) t.getFirst();
                    BooleanFormula sameAddress = generalEqual(load.getMemAddressExpr(), ((MemEvent) store).getMemAddressExpr(), ctx);
                    // Encode if load and store form an exclusive pair
                    BooleanFormula isPair = exclPair(load, store);
                    BooleanFormula pairingCond = pairingCond(load, store, maySet);
                    // For ARMv8, the store can be executed if addresses mismatch, but behaviour is "constrained unpredictable"
                    // The implementation does not include all possible unpredictable cases: in case of address
                    // mismatch, addresses of read and write are unknown, i.e. read and write can use any address.
                    // For RISCV and Power, addresses should match.
                    if (store.is(Tag.MATCHADDRESS)) {
                        pairingCond = bmgr.and(pairingCond, sameAddress);
                    } else {
                        unpredictable = bmgr.or(unpredictable, bmgr.and(store.exec(), isPair, bmgr.not(sameAddress)));
                    }
                    enc = bmgr.and(enc, bmgr.equivalence(isPair, pairingCond));
                    storeExec = bmgr.or(storeExec, isPair);
                }
                enc = bmgr.and(enc, bmgr.implication(store.exec(), storeExec));
            }
            for (Tuple tuple : encodeSets.get(rmw)) {
                MemEvent load = (MemEvent) tuple.getFirst();
                MemEvent store = (MemEvent) tuple.getSecond();
                BooleanFormula sameAddress = (alias.mustAlias(load, store) || store.is(Tag.MATCHADDRESS)) ? bmgr.makeTrue()
                        : generalEqual(load.getMemAddressExpr(), store.getMemAddressExpr(), ctx);
                enc = bmgr.and(enc, bmgr.equivalence(
                        rmw.getSMTVar(tuple, ctx),
                        // Relation between exclusive load and store
                        bmgr.and(store.exec(), exclPair(load, store), sameAddress)));
            }
            return bmgr.and(enc, bmgr.equivalence(Flag.ARM_UNPREDICTABLE_BEHAVIOUR.repr(ctx), unpredictable));
        }

        @Override
        public BooleanFormula visitSameAddress(Relation loc) {
            BooleanFormula enc = bmgr.makeTrue();
            for (Tuple tuple : encodeSets.get(loc)) {
                BooleanFormula rel = loc.getSMTVar(tuple, ctx);
                enc = bmgr.and(enc, bmgr.equivalence(rel, bmgr.and(
                        execution(tuple.getFirst(), tuple.getSecond(), exec, ctx),
                        Utils.generalEqual(
                                ((MemEvent) tuple.getFirst()).getMemAddressExpr(),
                                ((MemEvent) tuple.getSecond()).getMemAddressExpr(), ctx)
                )));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitReadFrom(Relation rf) {
            BooleanFormula enc = bmgr.makeTrue();
            Map<MemEvent, List<BooleanFormula>> edgeMap = new HashMap<>();
            for (Tuple tuple : ra.may(rf)) {
                MemEvent w = (MemEvent) tuple.getFirst();
                MemEvent r = (MemEvent) tuple.getSecond();
                BooleanFormula edge = rf.getSMTVar(tuple, ctx);
                // The boogie file might have a different type (Ints vs BVs) that the imposed by ARCH_PRECISION
                // In such cases we perform the transformation
                Formula a1 = w.getMemAddressExpr();
                Formula a2 = r.getMemAddressExpr();
                BooleanFormula sameAddress = generalEqual(a1, a2, ctx);
                Formula v1 = w.getMemValueExpr();
                Formula v2 = r.getMemValueExpr();
                BooleanFormula sameValue = generalEqual(v1, v2, ctx);
                edgeMap.computeIfAbsent(r, key -> new ArrayList<>()).add(edge);
                enc = bmgr.and(enc, bmgr.implication(edge, bmgr.and(execution(w, r, exec, ctx), sameAddress, sameValue)));
            }
            for (MemEvent r : edgeMap.keySet()) {
                enc = bmgr.and(enc, encodeEdgeSeq(r, edgeMap.get(r)));
            }
            return enc;
        }

        @Override
        public BooleanFormula visitMemoryOrder(Relation co) {
            return useSATEncoding ? encodeSAT(co) : encodeIDL(co);
        }

        private BooleanFormula pairingCond(Event load, Event store, TupleSet maySet) {
            BooleanFormula pairingCond = bmgr.and(load.exec(), store.cf());
            for (Tuple t : maySet.getBySecond(store)) {
                Event otherLoad = t.getFirst();
                if (otherLoad.getCId() > load.getCId()) {
                    pairingCond = bmgr.and(pairingCond, bmgr.not(otherLoad.exec()));
                }
            }
            for (Tuple t : maySet.getByFirst(load)) {
                Event otherStore = t.getSecond();
                if (otherStore.getCId() < store.getCId()) {
                    pairingCond = bmgr.and(pairingCond, bmgr.not(otherStore.cf()));
                }
            }
            return pairingCond;
        }

        private BooleanFormula exclPair(Event load, Event store) {
            return bmgr.makeVariable("excl(" + load.getCId() + "," + store.getCId() + ")");
        }

        private BooleanFormula encodeEdgeSeq(Event read, List<BooleanFormula> edges) {
            if (GlobalSettings.ALLOW_MULTIREADS) {
                return bmgr.implication(read.exec(), bmgr.or(edges));
            }
            int num = edges.size();
            int readId = read.getCId();
            BooleanFormula lastSeqVar = mkSeqVar(readId, 0);
            BooleanFormula newSeqVar = lastSeqVar;
            BooleanFormula atMostOne = bmgr.equivalence(lastSeqVar, edges.get(0));
            for (int i = 1; i < num; i++) {
                newSeqVar = mkSeqVar(readId, i);
                atMostOne = bmgr.and(atMostOne, bmgr.equivalence(newSeqVar, bmgr.or(lastSeqVar, edges.get(i))));
                atMostOne = bmgr.and(atMostOne, bmgr.not(bmgr.and(edges.get(i), lastSeqVar)));
                lastSeqVar = newSeqVar;
            }
            BooleanFormula atLeastOne = bmgr.or(newSeqVar, edges.get(edges.size() - 1));
            atLeastOne = bmgr.implication(read.exec(), atLeastOne);
            return bmgr.and(atMostOne, atLeastOne);
        }

        private BooleanFormula mkSeqVar(int readId, int i) {
            return bmgr.makeVariable("s(" + RF + ",E" + readId + "," + i + ")");
        }

        private BooleanFormula encodeIDL(Relation co) {
            List<MemEvent> allWrites = new ArrayList<>(Lists.transform(program.getCache().getEvents(FilterBasic.get(WRITE)), MemEvent.class::cast));
            allWrites.sort(Comparator.comparingInt(Event::getCId));
            TupleSet maxSet = ra.may(co);
            Set<Tuple> transCo = RelationAnalysis.findTransitivelyImpliedCo(co, exec);

            BooleanFormula enc = bmgr.makeTrue();
            // ---- Encode clock conditions (init = 0, non-init > 0) ----
            NumeralFormula.IntegerFormula zero = imgr.makeNumber(0);
            for (MemEvent w : allWrites) {
                NumeralFormula.IntegerFormula clock = coClockVar(w, ctx);
                enc = bmgr.and(enc, w.is(INIT) ? imgr.equal(clock, zero) : imgr.greaterThan(clock, zero));
            }

            // ---- Encode coherences ----
            for (int i = 0; i < allWrites.size() - 1; i++) {
                MemEvent w1 = allWrites.get(i);
                for (MemEvent w2 : allWrites.subList(i + 1, allWrites.size())) {
                    Tuple t = new Tuple(w1, w2);
                    boolean forwardPossible = maxSet.contains(t);
                    boolean backwardPossible = maxSet.contains(t.getInverse());
                    if (!forwardPossible && !backwardPossible) {
                        continue;
                    }

                    BooleanFormula execPair = execution(w1, w2, exec, ctx);
                    BooleanFormula sameAddress = alias.mustAlias(w1, w2) ? bmgr.makeTrue() :
                            generalEqual(w1.getMemAddressExpr(), w2.getMemAddressExpr(), ctx);
                    BooleanFormula pairingCond = bmgr.and(execPair, sameAddress);
                    BooleanFormula fCond = (w1.is(INIT) || transCo.contains(t)) ? bmgr.makeTrue() :
                            imgr.lessThan(coClockVar(w1, ctx), coClockVar(w2, ctx));
                    BooleanFormula bCond = (w2.is(INIT) || transCo.contains(t.getInverse())) ? bmgr.makeTrue() :
                            imgr.lessThan(coClockVar(w2, ctx), coClockVar(w1, ctx));
                    BooleanFormula coF = forwardPossible ? co.getSMTVar(w1, w2, ctx) : bmgr.makeFalse();
                    BooleanFormula coB = backwardPossible ? co.getSMTVar(w2, w1, ctx) : bmgr.makeFalse();

                    enc = bmgr.and(enc,
                            bmgr.implication(coF, fCond),
                            bmgr.implication(coB, bCond),
                            bmgr.equivalence(pairingCond, bmgr.or(coF, coB))
                    );
                }
            }

            return enc;
        }

        private BooleanFormula encodeSAT(Relation co) {
            List<MemEvent> allWrites = new ArrayList<>(Lists.transform(program.getCache().getEvents(FilterBasic.get(WRITE)), MemEvent.class::cast));
            allWrites.sort(Comparator.comparingInt(Event::getCId));
            TupleSet maxSet = ra.may(co);
            TupleSet minSet = ra.must(co);

            BooleanFormula enc = bmgr.makeTrue();
            // ---- Encode coherences ----
            for (int i = 0; i < allWrites.size() - 1; i++) {
                MemEvent w1 = allWrites.get(i);
                for (MemEvent w2 : allWrites.subList(i + 1, allWrites.size())) {
                    Tuple t = new Tuple(w1, w2);
                    Tuple tInv = t.getInverse();
                    boolean forwardPossible = maxSet.contains(t);
                    boolean backwardPossible = maxSet.contains(tInv);
                    if (!forwardPossible && !backwardPossible) {
                        continue;
                    }

                    BooleanFormula execPair = execution(w1, w2, exec, ctx);
                    BooleanFormula sameAddress = alias.mustAlias(w1, w2) ? bmgr.makeTrue() :
                            generalEqual(w1.getMemAddressExpr(), w2.getMemAddressExpr(), ctx);
                    BooleanFormula pairingCond = bmgr.and(execPair, sameAddress);
                    BooleanFormula coF = forwardPossible ? co.getSMTVar(w1, w2, ctx) : bmgr.makeFalse();
                    BooleanFormula coB = backwardPossible ? co.getSMTVar(w2, w1, ctx) : bmgr.makeFalse();

                    enc = bmgr.and(enc,
                            bmgr.equivalence(pairingCond, bmgr.or(coF, coB)),
                            bmgr.or(bmgr.not(coF), bmgr.not(coB))
                    );

                    if (!minSet.contains(t) && !minSet.contains(tInv)) {
                        for (MemEvent w3 : allWrites) {
                            Tuple t1 = new Tuple(w1, w3);
                            Tuple t2 = new Tuple(w3, w2);
                            if (forwardPossible && maxSet.contains(t1) && maxSet.contains(t2)) {
                                BooleanFormula co1 = co.getSMTVar(w1, w3, ctx);
                                BooleanFormula co2 = co.getSMTVar(w3, w2, ctx);
                                enc = bmgr.and(enc, bmgr.implication(bmgr.and(co1, co2), coF));
                            }
                            if (backwardPossible && maxSet.contains(t1.getInverse()) && maxSet.contains(t2.getInverse())) {
                                BooleanFormula co1 = co.getSMTVar(w2, w3, ctx);
                                BooleanFormula co2 = co.getSMTVar(w3, w1, ctx);
                                enc = bmgr.and(enc, bmgr.implication(bmgr.and(co1, co2), coB));
                            }
                        }
                    }
                }
            }

            return enc;
        }
    }
}
