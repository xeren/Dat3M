package com.dat3m.dartagnan.parsers.cat.visitors;

import com.dat3m.dartagnan.parsers.CatBaseVisitor;
import com.dat3m.dartagnan.parsers.CatVisitor;
import com.dat3m.dartagnan.parsers.CatParser;
import com.dat3m.dartagnan.parsers.cat.utils.ParsingException;
import com.dat3m.dartagnan.wmm.Filter;

public class VisitorFilter extends CatBaseVisitor<Filter> implements CatVisitor<Filter> {

	private final VisitorBase base;

	VisitorFilter(VisitorBase base) {
		this.base = base;
	}

	@Override
	public Filter visitExpr(CatParser.ExprContext ctx) {
		return ctx.e.accept(this);
	}

	@Override
	public Filter visitExprIntersection(CatParser.ExprIntersectionContext ctx) {
		return Filter.And.of(ctx.e1.accept(this), ctx.e2.accept(this));
	}

	@Override
	public Filter visitExprMinus(CatParser.ExprMinusContext ctx) {
		return Filter.Except.of(ctx.e1.accept(this), ctx.e2.accept(this));
	}

	@Override
	public Filter visitExprUnion(CatParser.ExprUnionContext ctx) {
		return Filter.Or.of(ctx.e1.accept(this), ctx.e2.accept(this));
	}

	@Override
	public Filter visitExprComplement(CatParser.ExprComplementContext ctx) {
		throw new RuntimeException("Filter complement is not implemented");
	}

	@Override
	public Filter visitExprBasic(CatParser.ExprBasicContext ctx) {
		String name = ctx.getText();
		Filter result = base.filter.get(name);
		return null != result ? result : Filter.of(name);
	}

	@Override
	public Filter visitExprCartesian(CatParser.ExprCartesianContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprComposition(CatParser.ExprCompositionContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprFencerel(CatParser.ExprFencerelContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprDomainIdentity(CatParser.ExprDomainIdentityContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprRangeIdentity(CatParser.ExprRangeIdentityContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprIdentity(CatParser.ExprIdentityContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprInverse(CatParser.ExprInverseContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprOptional(CatParser.ExprOptionalContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprTransitive(CatParser.ExprTransitiveContext ctx) {
		throw new ParsingException(ctx.getText());
	}

	@Override
	public Filter visitExprTransRef(CatParser.ExprTransRefContext ctx) {
		throw new ParsingException(ctx.getText());
	}
}
