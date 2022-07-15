package com.dat3m.dartagnan.encoding;

import com.dat3m.dartagnan.utils.dependable.DependencyGraph;
import com.dat3m.dartagnan.verification.Context;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.analysis.RelationAnalysis;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.utils.RecursiveGroup;
import com.dat3m.dartagnan.wmm.utils.RelationRepository;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.SolverContext;


public class WmmEncoder {

    private static final Logger logger = LogManager.getLogger(WmmEncoder.class);

    private final Wmm memoryModel;
    private final SolverContext ctx;

    // =====================================================================

    private WmmEncoder(Wmm memoryModel, Context context, SolverContext solverContext) {
        this.memoryModel = Preconditions.checkNotNull(memoryModel);
        context.requires(RelationAnalysis.class);
        ctx = solverContext;
    }

    public static WmmEncoder fromConfig(Wmm memoryModel, Context context, Configuration config, SolverContext ctx) throws InvalidConfigurationException {
        WmmEncoder encoder = new WmmEncoder(memoryModel, context, ctx);
        encoder.initializeEncoding();
        return encoder;
    }

    private void initializeEncoding() {
        for(String relName : Wmm.BASE_RELATIONS) {
            memoryModel.getRelationRepository().getRelation(relName);
        }

        for(RecursiveGroup recursiveGroup : memoryModel.getRecursiveGroups()){
            recursiveGroup.setDoRecurse();
        }

        for(Relation relation : memoryModel.getRelations()){
            relation.initializeEncoding(ctx);
        }

        for (Axiom axiom : memoryModel.getAxioms()) {
            axiom.initializeEncoding(ctx);
        }

        // ====================== Compute encoding information =================
        for (Axiom ax : memoryModel.getAxioms()) {
            ax.getRelation().addEncodeTupleSet(ax.getEncodeTupleSet());
        }

        for (RecursiveGroup recursiveGroup : Lists.reverse(memoryModel.getRecursiveGroups())) {
            recursiveGroup.updateEncodeTupleSets();
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
        final BooleanFormulaManager bmgr = ctx.getFormulaManager().getBooleanFormulaManager();
        final RelationRepository repo = memoryModel.getRelationRepository();
        final DependencyGraph<Relation> depGraph = DependencyGraph.from(
                Iterables.concat(
                        Iterables.transform(Wmm.BASE_RELATIONS, repo::getRelation), // base relations
                        Iterables.transform(memoryModel.getAxioms(), Axiom::getRelation) // axiom relations
                )
        );

        return depGraph.getNodeContents().stream().map(rel -> rel.encode(ctx)).reduce(bmgr.makeTrue(), bmgr::and);
    }

    // Encodes all axioms. This should be called after <encodeRelations>
    public BooleanFormula encodeConsistency() {
        logger.info("Encoding consistency");
        final BooleanFormulaManager bmgr = ctx.getFormulaManager().getBooleanFormulaManager();

        return memoryModel.getAxioms().stream()
                .filter(ax -> !ax.isFlagged())
                .map(ax -> ax.consistent(ctx))
                .reduce(bmgr.makeTrue(), bmgr::and);
    }
}
