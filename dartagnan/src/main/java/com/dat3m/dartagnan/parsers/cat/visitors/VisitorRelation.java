package com.dat3m.dartagnan.parsers.cat.visitors;

import com.dat3m.dartagnan.parsers.CatBaseVisitor;
import com.dat3m.dartagnan.parsers.CatParser;
import com.dat3m.dartagnan.parsers.CatVisitor;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.Relation;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.ID;

public class VisitorRelation extends CatBaseVisitor<Relation> implements CatVisitor<Relation> {

    private final VisitorBase base;

    VisitorRelation(VisitorBase base){
        this.base = base;
    }

    @Override
    public Relation visitExpr(CatParser.ExprContext ctx) {
        return ctx.e.accept(this);
    }

    @Override
    public Relation visitExprComposition(CatParser.ExprCompositionContext ctx) {
        Relation r1 = ctx.e1.accept(this);
        Relation r2 = ctx.e2.accept(this);
        return r1 == null || r2 == null ? null : base.relationRepository.composition(r1, r2);
    }

    @Override
    public Relation visitExprIntersection(CatParser.ExprIntersectionContext ctx) {
        Relation r1 = ctx.e1.accept(this);
        Relation r2 = ctx.e2.accept(this);
        return r1 == null || r2 == null ? null : base.relationRepository.intersection(r1, r2);
    }

    @Override
    public Relation visitExprMinus(CatParser.ExprMinusContext ctx) {
        Relation r1 = ctx.e1.accept(this);
        Relation r2 = ctx.e2.accept(this);
        return r1 == null || r2 == null ? null : base.relationRepository.difference(r1, r2);
    }

    @Override
    public Relation visitExprUnion(CatParser.ExprUnionContext ctx) {
        Relation r1 = ctx.e1.accept(this);
        Relation r2 = ctx.e2.accept(this);
        return r1 == null || r2 == null ? null : base.relationRepository.union(r1, r2);
    }

    @Override
    public Relation visitExprInverse(CatParser.ExprInverseContext ctx) {
        Relation r = ctx.e.accept(this);
        return r == null ? null : base.relationRepository.inverse(r);
    }

    @Override
    public Relation visitExprTransitive(CatParser.ExprTransitiveContext ctx) {
        Relation r = ctx.e.accept(this);
        return r == null ? null : base.relationRepository.transitive(r);
    }

    @Override
    public Relation visitExprTransRef(CatParser.ExprTransRefContext ctx) {
        Relation r = ctx.e.accept(this);
        return r == null ? null : base.relationRepository.union(base.relationRepository.getRelation(ID),base.relationRepository.transitive(r));
    }

    @Override
    public Relation visitExprDomainIdentity(CatParser.ExprDomainIdentityContext ctx) {
        Relation r = ctx.e.accept(this);
        return r == null ? null : base.relationRepository.domain(r);
    }

    @Override
    public Relation visitExprRangeIdentity(CatParser.ExprRangeIdentityContext ctx) {
        Relation r = ctx.e.accept(this);
        return r == null ? null : base.relationRepository.range(r);
    }

    @Override
    public Relation visitExprComplement(CatParser.ExprComplementContext ctx) {
        throw new RuntimeException("Relation complement is not implemented");
    }

    @Override
    public Relation visitExprOptional(CatParser.ExprOptionalContext ctx) {
        Relation r = ctx.e.accept(this);
        return r == null ? null : base.relationRepository.union(base.relationRepository.getRelation(ID), r);
    }

    @Override
    public Relation visitExprIdentity(CatParser.ExprIdentityContext ctx) {
        boolean orig = base.recursiveDef;
        base.recursiveDef = false;
        FilterAbstract filter = ctx.e.accept(base.filterVisitor);
        Relation relation = base.relationRepository.identity(filter);
        base.recursiveDef = orig;
        return relation;
    }

    @Override
    public Relation visitExprCartesian(CatParser.ExprCartesianContext ctx) {
        boolean orig = base.recursiveDef;
        base.recursiveDef = false;
        FilterAbstract filter1 = ctx.e1.accept(base.filterVisitor);
        FilterAbstract filter2 = ctx.e2.accept(base.filterVisitor);
        Relation relation = base.relationRepository.cartesian(filter1, filter2);
        base.recursiveDef = orig;
        return relation;
    }

    @Override
    public Relation visitExprFencerel(CatParser.ExprFencerelContext ctx) {
        return base.relationRepository.fence(ctx.n.getText());
    }

    @Override
    public Relation visitExprBasic(CatParser.ExprBasicContext ctx) {
        Relation relation = base.relationRepository.getRelation(ctx.n.getText());
        if(relation == null && base.recursiveDef){
            relation = base.relationRepository.recursive(ctx.n.getText());
        }
        return relation;
    }
}
