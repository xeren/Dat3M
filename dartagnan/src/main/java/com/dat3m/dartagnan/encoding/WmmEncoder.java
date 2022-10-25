package com.dat3m.dartagnan.encoding;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.Definition;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.NumeralFormula;

import java.util.*;
import java.util.stream.Stream;

import static com.dat3m.dartagnan.configuration.OptionNames.UNIQUE_READ;
import static com.dat3m.dartagnan.program.event.Tag.INIT;
import static com.dat3m.dartagnan.program.event.Tag.WRITE;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.CO;
import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.RF;
import static java.util.stream.Collectors.toList;

@Options
public class WmmEncoder implements Encoder {

    @Option(name = UNIQUE_READ,
            description = "Explicitly encodes the constraint that each load event reads from exactly one store." +
                    " Defaults to true." +
                    " Disabling it could enable spurious violations, i.e. if the memory model contains a flagged constraint. ",
            secure = true)
    private boolean uniqueReads = true;

    private static final Logger logger = LogManager.getLogger(WmmEncoder.class);

    private final EncodingContext context;
    final Map<Relation, Set<Tuple>> encodeSets = new HashMap<>();

    // =====================================================================

    private WmmEncoder(EncodingContext c) {
        context = c;
        c.getAnalysisContext().requires(RelationAnalysis.class);
    }

    public static WmmEncoder withContext(EncodingContext context) throws InvalidConfigurationException {
        WmmEncoder encoder = new WmmEncoder(context);
        context.getTask().getConfig().inject(encoder);
        encoder.initializeEncodeSets();
        return encoder;
    }

    public BooleanFormula encodeFullMemoryModel() {
        return context.getBooleanFormulaManager().and(
                encodeReadFrom(),
                encodeMemoryOrder(),
                encodeRelations(),
                encodeConsistency()
        );
    }

    // Initializes everything just like encodeAnarchicSemantics but also encodes all
    // relations that are needed for the axioms (but does NOT encode the axioms themselves yet)
    // NOTE: It avoids encoding relations that do NOT affect the axioms, i.e. unused relations
    public BooleanFormula encodeRelations() {
        logger.info("Encoding relations");
        final RelationEncoder v = new RelationEncoder();
        final List<BooleanFormula> enc = new ArrayList<>();
        for (final Map.Entry<Relation, Set<Tuple>> entry : encodeSets.entrySet()) {
            final Relation r = entry.getKey();
            final TupleEncoder e = r.accept(v);
            if (e != null) {
                for (final Tuple t : entry.getValue()) {
                    enc.add(v.bmgr.equivalence(context.edge(r, t), e.encode(t)));
                }
            }
        }
        return v.bmgr.and(enc);
    }

    // Encodes all axioms. This should be called after <encodeRelations>
    public BooleanFormula encodeConsistency() {
        logger.info("Encoding consistency");
        Wmm memoryModel = context.getTask().getMemoryModel();
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        return memoryModel.getAxioms().stream()
                .filter(ax -> !ax.isInactive() && !ax.isFlagged())
                .map(ax -> ax.consistent(context))
                .reduce(bmgr.makeTrue(), bmgr::and);
    }

    public BooleanFormula encodeReadFrom() {
        logger.info("Encoding read-from");
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        final List<BooleanFormula> enc = new ArrayList<>();
        final Map<MemEvent, List<BooleanFormula>> edgeMap = new HashMap<>();
        final RelationAnalysis ra = context.getAnalysisContext().get(RelationAnalysis.class);
        final Relation rf = context.getTask().getMemoryModel().getRelation(RF);
        for (final Tuple tuple : ra.getKnowledge(rf).getMaySet()) {
            final MemEvent w = (MemEvent) tuple.getFirst();
            final MemEvent r = (MemEvent) tuple.getSecond();
            final BooleanFormula edge = context.edge(rf, tuple);
            final BooleanFormula sameAddress = context.sameAddress(w, r);
            final BooleanFormula sameValue = context.equal(context.value(w), context.value(r));
            edgeMap.computeIfAbsent(r, key -> new ArrayList<>()).add(edge);
            enc.add(bmgr.implication(edge, bmgr.and(context.execution(w, r), sameAddress, sameValue)));
        }
        for (final Map.Entry<MemEvent, List<BooleanFormula>> e : edgeMap.entrySet()) {
            final MemEvent r = e.getKey();
            final List<BooleanFormula> edges = e.getValue();
            if (!uniqueReads) {
                enc.add(bmgr.implication(context.execution(r), bmgr.or(edges)));
                continue;
            }
            final int num = edges.size();
            final String prefix = "s(rf,E" + r.getCId() + ",";
            BooleanFormula lastSeqVar = edges.get(0);
            for (int i = 1; i < num; i++) {
                final BooleanFormula edge = edges.get(i);
                final BooleanFormula newSeqVar = bmgr.makeVariable(prefix + i + ")");
                enc.add(bmgr.equivalence(newSeqVar, bmgr.or(lastSeqVar, edge)));
                enc.add(bmgr.not(bmgr.and(edge, lastSeqVar)));
                lastSeqVar = newSeqVar;
            }
            enc.add(bmgr.implication(context.execution(r), lastSeqVar));
        }
        return bmgr.and(enc);
    }

    public BooleanFormula encodeMemoryOrder() {
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        final RelationAnalysis ra = context.getAnalysisContext().get(RelationAnalysis.class);
        final Relation co = context.getTask().getMemoryModel().getRelation(CO);
        final IntegerFormulaManager imgr = context.getFormulaManager().getIntegerFormulaManager();
        final List<MemEvent> allWrites = context.getTask().getProgram().getCache().getEvents(FilterBasic.get(WRITE)).stream()
                .map(MemEvent.class::cast)
                .sorted(Comparator.comparingInt(Event::getCId))
                .collect(toList());
        final RelationAnalysis.Knowledge k = ra.getKnowledge(co);
        final Set<Tuple> transCo = ra.findTransitivelyImpliedCo(co);
        final List<BooleanFormula> enc = new ArrayList<>();
        // ---- Encode clock conditions (init = 0, non-init > 0) ----
        final NumeralFormula.IntegerFormula zero = imgr.makeNumber(0);
        final boolean useIDLEncoding = !context.useSATEncoding;
        if (useIDLEncoding) {
            for (final MemEvent w : allWrites) {
                final NumeralFormula.IntegerFormula clock = context.memoryOrderClock(w);
                enc.add(w.is(INIT) ? imgr.equal(clock, zero) : imgr.greaterThan(clock, zero));
            }
        }
        // ---- Encode coherences ----
        for (int i = 0; i < allWrites.size() - 1; i++) {
            final MemEvent w1 = allWrites.get(i);
            for (final MemEvent w2 : allWrites.subList(i + 1, allWrites.size())) {
                final Tuple t = new Tuple(w1, w2);
                final Tuple tInv = t.getInverse();
                final boolean forwardPossible = k.getMaySet().contains(t);
                final boolean backwardPossible = k.getMaySet().contains(tInv);
                if (!forwardPossible && !backwardPossible) {
                    continue;
                }
                final BooleanFormula execPair = context.execution(w1, w2);
                final BooleanFormula sameAddress = context.sameAddress(w1, w2);
                final BooleanFormula pairingCond = bmgr.and(execPair, sameAddress);
                final BooleanFormula coF = forwardPossible ? context.edge(co, t) : bmgr.makeFalse();
                final BooleanFormula coB = backwardPossible ? context.edge(co, tInv) : bmgr.makeFalse();
                enc.add(bmgr.equivalence(pairingCond, bmgr.or(coF, coB)));
                if (useIDLEncoding) {
                    final BooleanFormula fCond = (w1.is(INIT) || transCo.contains(t)) ? bmgr.makeTrue() :
                            imgr.lessThan(context.memoryOrderClock(w1), context.memoryOrderClock(w2));
                    final BooleanFormula bCond = (w2.is(INIT) || transCo.contains(tInv)) ? bmgr.makeTrue() :
                            imgr.lessThan(context.memoryOrderClock(w2), context.memoryOrderClock(w1));
                    enc.add(bmgr.implication(coF, fCond));
                    enc.add(bmgr.implication(coB, bCond));
                    continue;
                }
                enc.add(bmgr.not(bmgr.and(coF, coB)));
                if (k.getMustSet().contains(t) || k.getMustSet().contains(tInv)) {
                    continue;
                }
                for (final MemEvent w3 : allWrites) {
                    final Tuple t1 = new Tuple(w1, w3);
                    final Tuple t2 = new Tuple(w3, w2);
                    if (forwardPossible && k.getMaySet().contains(t1) && k.getMaySet().contains(t2)) {
                        final BooleanFormula co1 = context.edge(co, t1);
                        final BooleanFormula co2 = context.edge(co, t2);
                        enc.add(bmgr.implication(bmgr.and(co1, co2), coF));
                    }
                    final Tuple t1Inv = t1.getInverse();
                    final Tuple t2Inv = t2.getInverse();
                    if (backwardPossible && k.getMaySet().contains(t1Inv) && k.getMaySet().contains(t2Inv)) {
                        final BooleanFormula co1 = context.edge(co, t2Inv);
                        final BooleanFormula co2 = context.edge(co, t1Inv);
                        enc.add(bmgr.implication(bmgr.and(co1, co2), coB));
                    }
                }
            }
        }
        return bmgr.and(enc);
    }

    private interface TupleEncoder {
        BooleanFormula encode(Tuple tuple);
    }

    private final class RelationEncoder implements Definition.Visitor<TupleEncoder> {
        final Program program = context.getTask().getProgram();
        final RelationAnalysis ra = context.getAnalysisContext().requires(RelationAnalysis.class);
        final BooleanFormulaManager bmgr = context.getBooleanFormulaManager();
        @Override
        public TupleEncoder visitDefinition(Relation rel, List<? extends Relation> dependencies) {
            return null;
        }
        @Override
        public TupleEncoder visitUnion(Relation rel, Relation... r) {
            return tuple -> {
                List<BooleanFormula> opt = new ArrayList<>(r.length);
                for (Relation relation : r) {
                    opt.add(edge(relation, tuple));
                }
                return bmgr.or(opt);
            };
        }
        @Override
        public TupleEncoder visitIntersection(Relation rel, Relation... r) {
            return tuple -> {
                List<BooleanFormula> opt = new ArrayList<>(r.length);
                for (Relation relation : r) {
                    opt.add(edge(relation, tuple));
                }
                return bmgr.and(opt);
            };
        }
        @Override
        public TupleEncoder visitDifference(Relation rel, Relation r1, Relation r2) {
            return tuple -> {
                BooleanFormula opt1 = edge(r1, tuple);
                BooleanFormula opt2 = bmgr.not(edge(r2, tuple));
                return bmgr.and(opt1, opt2);
            };
        }
        @Override
        public TupleEncoder visitComposition(Relation rel, Relation r1, Relation r2) {
            final RelationAnalysis.Knowledge k1 = ra.getKnowledge(r1);
            final RelationAnalysis.Knowledge k2 = ra.getKnowledge(r2);
            return tuple -> {
                BooleanFormula expr = bmgr.makeFalse();
                for (Tuple t1 : k1.getMaySet().getByFirst(tuple.getFirst())) {
                    Tuple t2 = new Tuple(t1.getSecond(), tuple.getSecond());
                    if (k2.getMaySet().contains(t2)) {
                        expr = bmgr.or(expr, bmgr.and(edge(r1, t1), edge(r2, t2)));
                    }
                }
                return expr;
            };
        }
        @Override
        public TupleEncoder visitDomainIdentity(Relation rel, Relation r1) {
            final RelationAnalysis.Knowledge k1 = ra.getKnowledge(r1);
            return tuple -> {
                BooleanFormula opt = bmgr.makeFalse();
                for (Tuple t : k1.getMaySet().getByFirst(tuple.getFirst())) {
                    opt = bmgr.or(opt, edge(r1, t));
                }
                return opt;
            };
        }
        @Override
        public TupleEncoder visitRangeIdentity(Relation rel, Relation r1) {
            final RelationAnalysis.Knowledge k1 = ra.getKnowledge(r1);
            return tuple -> {
                BooleanFormula opt = bmgr.makeFalse();
                for (Tuple t : k1.getMaySet().getBySecond(tuple.getFirst())) {
                    opt = bmgr.or(opt, edge(r1, t));
                }
                return opt;
            };
        }
        @Override
        public TupleEncoder visitTransitiveClosure(Relation rel, Relation r1) {
            final RelationAnalysis.Knowledge k = ra.getKnowledge(rel);
            final RelationAnalysis.Knowledge k1 = ra.getKnowledge(r1);
            return tuple -> {
                BooleanFormula orClause = bmgr.makeFalse();
                Event e1 = tuple.getFirst();
                Event e2 = tuple.getSecond();
                if (k1.getMaySet().contains(tuple)) {
                    orClause = bmgr.or(orClause, edge(r1, tuple));
                }
                for (Tuple t : k1.getMaySet().getByFirst(e1)) {
                    Event e3 = t.getSecond();
                    if (e3.getCId() != e1.getCId() && e3.getCId() != e2.getCId() && k.getMaySet().contains(new Tuple(e3, e2))) {
                        BooleanFormula tVar = k.getMustSet().contains(t) ? context.execution(e1) : edge(r1, t);
                        orClause = bmgr.or(orClause, bmgr.and(tVar, edge(rel, new Tuple(e3, e2))));
                    }
                }
                return orClause;
            };
        }
        @Override
        public TupleEncoder visitFences(Relation rel, FilterAbstract fenceSet) {
            final List<Event> fences = program.getCache().getEvents(fenceSet);
            return tuple -> {
                Event e1 = tuple.getFirst();
                Event e2 = tuple.getSecond();
                List<BooleanFormula> orClause = new ArrayList<>();
                for (Event f : fences) {
                    if (e1.getCId() < f.getCId() && f.getCId() < e2.getCId()) {
                        orClause.add(context.execution(f));
                    }
                }
                return bmgr.and(execution(tuple), bmgr.or(orClause));
            };
        }
        @Override
        public TupleEncoder visitCriticalSections(Relation rscs) {
            final RelationAnalysis.Knowledge k = ra.getKnowledge(rscs);
            return tuple -> {
                Event lock = tuple.getFirst();
                Event unlock = tuple.getSecond();
                BooleanFormula relation = execution(tuple);
                for (Tuple t : k.getMaySet().getBySecond(unlock)) {
                    Event y = t.getFirst();
                    if (lock.getCId() < y.getCId() && y.getCId() < unlock.getCId()) {
                        relation = bmgr.and(relation, bmgr.not(edge(rscs, t)));
                    }
                }
                for (Tuple t : k.getMaySet().getByFirst(lock)) {
                    Event y = t.getSecond();
                    if (lock.getCId() < y.getCId() && y.getCId() < unlock.getCId()) {
                        relation = bmgr.and(relation, bmgr.not(edge(rscs, t)));
                    }
                }
                return relation;
            };
        }
        @Override
        public TupleEncoder visitReadModifyWrites(Relation rmw) {
            return tuple -> {
                MemEvent load = (MemEvent) tuple.getFirst();
                MemEvent store = (MemEvent) tuple.getSecond();
                BooleanFormula sameAddress = store.is(Tag.MATCHADDRESS) ? bmgr.makeTrue() : context.sameAddress(load, store);
                return bmgr.and(
                        context.execution(store),
                        context.exclusivePairVariable(load, store),
                        sameAddress);
            };
        }
        @Override
        public TupleEncoder visitSameAddress(Relation loc) {
            return tuple -> {
                MemEvent e1 = (MemEvent) tuple.getFirst();
                MemEvent e2 = (MemEvent) tuple.getSecond();
                BooleanFormula sameAddress = context.sameAddress(e1, e2);
                return bmgr.and(execution(tuple), sameAddress);
            };
        }
        private BooleanFormula edge(Relation relation, Tuple tuple) {
            return context.edge(relation, tuple);
        }
        private BooleanFormula execution(Tuple tuple) {
            return context.execution(tuple.getFirst(), tuple.getSecond());
        }
    }

    private void initializeEncodeSets() {
        for (Relation r : context.getTask().getMemoryModel().getRelations()) {
            encodeSets.put(r, new HashSet<>());
        }
        EncodeSets v = new EncodeSets(context.getAnalysisContext());
        Map<Relation, List<Stream<Tuple>>> queue = new HashMap<>();
        for (Axiom a : context.getTask().getMemoryModel().getAxioms()) {
            for (Map.Entry<Relation, Set<Tuple>> e : a.getEncodeTupleSets(context.getTask(), context.getAnalysisContext()).entrySet()) {
                queue.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue().stream());
            }
        }
        while (!queue.isEmpty()) {
            Relation r = queue.keySet().iterator().next();
            Set<Tuple> s = encodeSets.get(r);
            List<Tuple> c = new ArrayList<>();
            for (Stream<Tuple> news : queue.remove(r)) {
                news.filter(s::add).forEach(c::add);
            }
            if (!c.isEmpty()) {
                v.news = c;
                for (Map.Entry<Relation, Stream<Tuple>> e : r.accept(v).entrySet()) {
                    queue.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
                }
            }
        }
    }
}
