package com.dat3m.dartagnan.parsers.cat.visitors;

import com.dat3m.dartagnan.parsers.CatBaseVisitor;
import com.dat3m.dartagnan.parsers.CatParser;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.RecursiveRelation;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.relation.base.stat.RelCartesian;
import com.dat3m.dartagnan.wmm.relation.base.stat.RelFencerel;
import com.dat3m.dartagnan.wmm.relation.base.stat.RelSetIdentity;
import com.dat3m.dartagnan.wmm.relation.binary.RelComposition;
import com.dat3m.dartagnan.wmm.relation.binary.RelIntersection;
import com.dat3m.dartagnan.wmm.relation.binary.RelMinus;
import com.dat3m.dartagnan.wmm.relation.binary.RelUnion;
import com.dat3m.dartagnan.wmm.relation.unary.*;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.ID;

public class VisitorRelation extends CatBaseVisitor<Relation> {

    private final VisitorBase base;

    VisitorRelation(VisitorBase base) {
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
        return r1 == null || r2 == null ? null : added(new RelComposition(r1, r2));
    }

    @Override
    public Relation visitExprIntersection(CatParser.ExprIntersectionContext ctx) {
        Relation r1 = ctx.e1.accept(this);
        Relation r2 = ctx.e2.accept(this);
        return r1 == null || r2 == null ? null : added(new RelIntersection(r1, r2));
    }

    @Override
    public Relation visitExprMinus(CatParser.ExprMinusContext ctx) {
        Relation r1 = ctx.e1.accept(this);
        Relation r2 = ctx.e2.accept(this);
        return r1 == null || r2 == null ? null : added(new RelMinus(r1, r2));
    }

    @Override
    public Relation visitExprUnion(CatParser.ExprUnionContext ctx) {
        Relation r1 = ctx.e1.accept(this);
        Relation r2 = ctx.e2.accept(this);
        return r1 == null || r2 == null ? null : added(new RelUnion(r1, r2));
    }

    @Override
    public Relation visitExprInverse(CatParser.ExprInverseContext ctx) {
        Relation r1 = ctx.e.accept(this);
        return r1 == null ? null : added(new RelInverse(r1));
    }

    @Override
    public Relation visitExprTransitive(CatParser.ExprTransitiveContext ctx) {
        Relation r1 = ctx.e.accept(this);
        return r1 == null ? null : added(new RelTrans(r1));
    }

    @Override
    public Relation visitExprTransRef(CatParser.ExprTransRefContext ctx) {
        Relation r1 = ctx.e.accept(this);
        return r1 == null ? null : added(new RelTransRef(r1));
    }

    @Override
    public Relation visitExprDomainIdentity(CatParser.ExprDomainIdentityContext ctx) {
        Relation r1 = ctx.e.accept(this);
        return r1 == null ? null : added(new RelDomainIdentity(r1));
    }

    @Override
    public Relation visitExprRangeIdentity(CatParser.ExprRangeIdentityContext ctx) {
        Relation r1 = ctx.e.accept(this);
        return r1 == null ? null : added(new RelRangeIdentity(r1));
    }

    @Override
    public Relation visitExprComplement(CatParser.ExprComplementContext ctx) {
        Relation r2 = ctx.e.accept(this);
        if (r2 == null) {
            return null;
        }
        FilterBasic f = FilterBasic.get(Tag.VISIBLE);
        Relation r1 = added(new RelCartesian(f, f));
        return added(new RelMinus(r1, r2));
    }

    @Override
    public Relation visitExprOptional(CatParser.ExprOptionalContext ctx) {
        Relation r1 = ctx.e.accept(this);
        return r1 == null ? null : added(new RelUnion(base.wmm.getRelation(ID), r1));
    }

    @Override
    public Relation visitExprIdentity(CatParser.ExprIdentityContext ctx) {
        boolean orig = base.recursiveDef;
        base.recursiveDef = false;
        FilterAbstract filter = ctx.e.accept(base.filterVisitor);
        Relation relation = added(new RelSetIdentity(filter));
        base.recursiveDef = orig;
        return relation;
    }

    @Override
    public Relation visitExprCartesian(CatParser.ExprCartesianContext ctx) {
        boolean orig = base.recursiveDef;
        base.recursiveDef = false;
        FilterAbstract filter1 = ctx.e1.accept(base.filterVisitor);
        FilterAbstract filter2 = ctx.e2.accept(base.filterVisitor);
        Relation relation = added(new RelCartesian(filter1, filter2));
        base.recursiveDef = orig;
        return relation;
    }

    @Override
    public Relation visitExprFencerel(CatParser.ExprFencerelContext ctx) {
        return added(new RelFencerel(ctx.n.getText()));
    }

    @Override
    public Relation visitExprBasic(CatParser.ExprBasicContext ctx) {
        Relation r = base.wmm.getRelation(ctx.n.getText());
        return r != null || !base.recursiveDef ? r : added(new RecursiveRelation(ctx.n.getText()));
    }

    private Relation added(Relation r) {
        base.wmm.addRelation(r);
        return r;
    }
}
