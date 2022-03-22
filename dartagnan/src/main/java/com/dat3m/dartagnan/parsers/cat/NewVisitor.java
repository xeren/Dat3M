package com.dat3m.dartagnan.parsers.cat;

import com.dat3m.dartagnan.parsers.NewCatBaseVisitor;
import com.dat3m.dartagnan.parsers.NewCatParser.*;
import com.dat3m.dartagnan.parsers.NewCatVisitor;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.filter.*;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.example.*;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.NewWmm;
import com.dat3m.dartagnan.wmm.analysis.newRelationAnalysis.newWmm.Relation;

import java.util.HashMap;
import java.util.List;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.*;
import static java.util.stream.Collectors.toList;

public class NewVisitor extends NewCatBaseVisitor<Object> implements NewCatVisitor<Object> {

    final NewWmm wmm = NewWmm.createEmptyWmm();
    private Relation current;
    private Relation id;
    private Relation programOrder;
    private Relation dependency;
    private Relation controlDependency;
    private Relation sameAddress;
    private Relation readFrom;
    private Relation memoryOrder;
    private Relation fromRead;
    private Relation external;
    private Relation internal;
    private final HashMap<String,Object> namespace = new HashMap<>();

    @Override
    public NewWmm visitMcm(McmContext ctx) {
        super.visitMcm(ctx);
        return wmm;
    }

    @Override
    public Void visitAcyclicDefinition(AcyclicDefinitionContext ctx) {
        Relation r = (Relation)ctx.e.accept(this);
        wmm.addAxiom(new Acyclic(r));
        return null;
    }

    @Override
    public Void visitIrreflexiveDefinition(IrreflexiveDefinitionContext ctx) {
        Relation r = (Relation)ctx.e.accept(this);
        wmm.addAxiom(new Irreflexive(r));
        return null;
    }

    @Override
    public Void visitEmptyDefinition(EmptyDefinitionContext ctx) {
        Relation r = (Relation)ctx.e.accept(this);
        wmm.addAxiom(new Empty(r));
        return null;
    }

    @Override
    public Void visitLetDefinition(LetDefinitionContext ctx) {
        String n = ctx.n.getText();
        current = wmm.newRelationWithName(n);
        Object o = ctx.e.accept(this);
        if(o instanceof FilterAbstract) {
            ((FilterAbstract)o).setName(n);
            wmm.deleteRelation(current);
            current = null;
        }
        namespace.put(n,o);
        return null;
    }

    @Override
    public Void visitLetRecDefinition(LetRecDefinitionContext ctx) {
        List<Relation> relation = ctx.NAME().stream().map(n->wmm.newRelationWithName(n.getText())).collect(toList());
        for(Relation r : relation) {
            namespace.put(r.getName(),r);
        }
        for(int i = 0; i < relation.size(); i++) {
            current = relation.get(i);
            ctx.expression(i).accept(this);
        }
        return null;
    }

    @Override
    public Object visitExpr(ExprContext ctx) {
        return ctx.e.accept(this);
    }

    @Override
    public Relation visitExprComposition(ExprCompositionContext ctx) {
        Relation relation = getCurrent();
        Relation first = (Relation)ctx.e1.accept(this);
        Relation second = (Relation)ctx.e2.accept(this);
        wmm.addDefinition(new Composition(relation,first,second));
        return relation;
    }

    @Override
    public Object visitExprIntersection(ExprIntersectionContext ctx) {
        Relation relation = getCurrent();
        Object lhs = ctx.e1.accept(this);
        Object rhs = ctx.e2.accept(this);
        if(lhs instanceof FilterAbstract) {
            assert rhs instanceof FilterAbstract;
            return FilterIntersection.get((FilterAbstract)lhs,(FilterAbstract)rhs);
        }
        assert lhs instanceof Relation && rhs instanceof Relation;
        wmm.addDefinition(new Intersection(relation,(Relation)lhs,(Relation)rhs));
        return relation;
    }

    @Override
    public Object visitExprMinus(ExprMinusContext ctx) {
        Relation relation = getCurrent();
        Object lhs = ctx.e1.accept(this);
        Object rhs = ctx.e2.accept(this);
        if(lhs instanceof FilterAbstract) {
            assert rhs instanceof FilterAbstract;
            return FilterMinus.get((FilterAbstract)lhs,(FilterAbstract)rhs);
        }
        assert lhs instanceof Relation && rhs instanceof Relation;
        wmm.addDefinition(new Difference(relation,(Relation)lhs,(Relation)rhs));
        return relation;
    }

    @Override
    public Object visitExprUnion(ExprUnionContext ctx) {
        Relation relation = getCurrent();
        Object lhs = ctx.e1.accept(this);
        Object rhs = ctx.e2.accept(this);
        if(lhs instanceof FilterAbstract) {
            assert rhs instanceof FilterAbstract;
            return FilterUnion.get((FilterAbstract)lhs,(FilterAbstract)rhs);
        }
        assert lhs instanceof Relation && rhs instanceof Relation;
        wmm.addDefinition(new Union(relation,(Relation)lhs,(Relation)rhs));
        return relation;
    }

    @Override
    public Relation visitExprInverse(ExprInverseContext ctx) {
        Relation relation = getCurrent();
        Relation child = (Relation)ctx.e.accept(this);
        wmm.addDefinition(new Inverse(relation,child));
        return relation;
    }

    @Override
    public Relation visitExprTransitive(ExprTransitiveContext ctx) {
        Relation relation = getCurrent();
        Relation child = (Relation)ctx.e.accept(this);
        Relation composition = wmm.newRelation();
        wmm.addDefinition(new Composition(composition,relation,relation));
        wmm.addDefinition(new Union(relation,child,composition));
        return relation;
    }

    @Override
    public Relation visitExprTransRef(ExprTransRefContext ctx) {
        Relation relation = getCurrent();
        Relation id = getOrCreateId();
        Relation child = (Relation)ctx.e.accept(this);
        Relation composition = wmm.newRelation();
        wmm.addDefinition(new Composition(composition,relation,relation));
        wmm.addDefinition(new Union(relation,id,child,composition));
        return relation;
    }

    @Override
    public Relation visitExprDomainIdentity(ExprDomainIdentityContext ctx) {
        throw new UnsupportedOperationException("domain operator");
    }

    @Override
    public Relation visitExprRangeIdentity(ExprRangeIdentityContext ctx) {
        throw new UnsupportedOperationException("range operator");
    }

    @Override
    public Object visitExprComplement(ExprComplementContext ctx) {
        Relation relation = getCurrent();
        Object child = ctx.e.accept(this);
        if(child instanceof FilterAbstract) {
            return FilterMinus.get(FilterBasic.get(Tag.ANY),(FilterAbstract)child);
        }
        assert child instanceof Relation;
        Relation full = wmm.newRelation();
        FilterBasic any = FilterBasic.get(Tag.ANY);
        wmm.addDefinition(new Cartesian(full,any,any));
        wmm.addDefinition(new Difference(relation,full,(Relation)child));
        return relation;
    }

    @Override
    public Relation visitExprOptional(ExprOptionalContext ctx) {
        Relation relation = getCurrent();
        Relation child = (Relation)ctx.e.accept(this);
        Relation id = getOrCreateId();
        wmm.addDefinition(new Union(relation,child,id));
        return relation;
    }

    @Override
    public Relation visitExprIdentity(ExprIdentityContext ctx) {
        Relation relation = getCurrent();
        FilterAbstract filter = (FilterAbstract)ctx.e.accept(this);
        wmm.addDefinition(new SetIdentity(relation,filter));
        return relation;
    }

    @Override
    public Relation visitExprCartesian(ExprCartesianContext ctx) {
        Relation relation = getCurrent();
        FilterAbstract filter1 = (FilterAbstract)ctx.e1.accept(this);
        FilterAbstract filter2 = (FilterAbstract)ctx.e2.accept(this);
        wmm.addDefinition(new Cartesian(relation,filter1,filter2));
        return relation;
    }

    @Override
    public Relation visitExprFencerel(ExprFencerelContext ctx) {
        Relation relation = getCurrent();
        wmm.addDefinition(new Fenced(relation,FilterBasic.get(ctx.n.getText())));
        return relation;
    }

    @Override
    public Object visitExprBasic(ExprBasicContext ctx) {
        return namespace.computeIfAbsent(ctx.n.getText(),this::predefined);
    }

    private Relation getCurrent() {
        if(current == null) {
            return wmm.newRelation();
        }
        Relation result = current;
        current = null;
        return result;
    }

    private Relation getOrCreateId() {
        if(id == null) {
            id = wmm.newRelationWithName(ID);
            wmm.addDefinition(new SetIdentity(id,FilterBasic.get(Tag.ANY)));
        }
        return id;
    }

    private Relation getOrCreateProgramOrder() {
        if(programOrder == null) {
            programOrder = wmm.newRelationWithName(PO);
        }
        return programOrder;
    }

    private Relation getOrCreateIdd() {
        if(dependency == null) {
            Relation child = wmm.newRelationWithName(IDD);
            Relation comp = wmm.newRelation();
            dependency = wmm.newRelation();
            wmm.addDefinition(new Composition(comp,dependency,dependency));
            wmm.addDefinition(new Union(dependency,child,comp));
        }
        return dependency;
    }

    private Relation getOrCreateControlDependency() {
        if(controlDependency == null) {
            controlDependency = wmm.newRelationWithName(CTRL);
            Relation child = wmm.newRelation();
            Relation filter = wmm.newRelation();
            wmm.addDefinition(new Cartesian(filter,FilterBasic.get(Tag.READ),FilterBasic.get(Tag.VISIBLE)));
            wmm.addDefinition(new Composition(child,getOrCreateIdd(),wmm.newRelationWithName(CTRLDIRECT)));
            wmm.addDefinition(new Intersection(controlDependency,child,filter));
        }
        return controlDependency;
    }

    private Relation getOrCreateSameAddress() {
        if(sameAddress == null) {
            sameAddress = wmm.newRelationWithName(LOC);
            wmm.addDefinition(new SameAddress(sameAddress));
        }
        return sameAddress;
    }

    private Relation getOrCreateReadFrom() {
        if(readFrom == null) {
            readFrom = wmm.newRelationWithName(RF);
            ReadFrom constraint = new ReadFrom(readFrom);
            wmm.addDefinition(constraint);
            wmm.addAxiom(constraint);
        }
        return readFrom;
    }

    private Relation getOrCreateMemoryOrder() {
        if(memoryOrder == null) {
            memoryOrder = wmm.newRelationWithName(CO);
            wmm.addDefinition(new MemoryOrder(memoryOrder));
        }
        return memoryOrder;
    }

    private Relation getOrCreateFromRead() {
        if(fromRead == null) {
            fromRead = wmm.newRelationWithName(FR);
            Relation inverse = wmm.newRelation();
            wmm.addDefinition(new Inverse(inverse,getOrCreateReadFrom()));
            wmm.addDefinition(new Composition(fromRead,inverse,getOrCreateMemoryOrder()));
        }
        return fromRead;
    }

    private Relation getOrCreateExternal() {
        if(external == null) {
            external = wmm.newRelationWithName(EXT);
        }
        return external;
    }

    private Relation getOrCreateInternal() {
        if(internal == null) {
            internal = wmm.newRelationWithName(INT);
        }
        return internal;
    }

    private Object predefined(String name) {
        Relation relation;
        Relation child;
        Relation filter;
        switch(name) {
        case EMPTY:
        case RMW:
        case CRIT:
            return wmm.newRelationWithName(name);
        case ID:
            return getOrCreateId();
        case PO:
            return getOrCreateProgramOrder();
        case EXT:
            return getOrCreateExternal();
        case INT:
            return getOrCreateInternal();
        case LOC:
            return getOrCreateSameAddress();
        case POLOC:
            relation = wmm.newRelationWithName(POLOC);
            child = getOrCreateSameAddress();
            filter = getOrCreateProgramOrder();
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case RF:
            return getOrCreateReadFrom();
        case RFE:
            relation = wmm.newRelationWithName(RFE);
            child = getOrCreateReadFrom();
            filter = getOrCreateExternal();
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case RFI:
            relation = wmm.newRelationWithName(RFI);
            child = getOrCreateReadFrom();
            filter = getOrCreateInternal();
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case CO:
            return getOrCreateMemoryOrder();
        case COE:
            relation = wmm.newRelationWithName(COE);
            child = getOrCreateMemoryOrder();
            filter = getOrCreateExternal();
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case COI:
            relation = wmm.newRelationWithName(COI);
            child = getOrCreateMemoryOrder();
            filter = getOrCreateInternal();
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case FR:
            return getOrCreateFromRead();
        case FRE:
            relation = wmm.newRelationWithName(FRE);
            child = getOrCreateFromRead();
            filter = getOrCreateExternal();
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case FRI:
            relation = wmm.newRelationWithName(FRI);
            child = getOrCreateFromRead();
            filter = getOrCreateInternal();
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case DATA:
            relation = wmm.newRelationWithName(DATA);
            filter = wmm.newRelation();
            wmm.addDefinition(new Cartesian(filter,FilterBasic.get(Tag.READ),FilterBasic.get(Tag.WRITE)));
            wmm.addDefinition(new Intersection(relation,getOrCreateIdd(),filter));
            return relation;
        case ADDR:
            relation = wmm.newRelationWithName(ADDR);
            child = wmm.newRelation();
            filter = wmm.newRelation();
            wmm.addDefinition(new Cartesian(filter,FilterBasic.get(Tag.READ),FilterBasic.get(Tag.MEMORY)));
            wmm.addDefinition(new Composition(child,getOrCreateIdd(),wmm.newRelationWithName(ADDRDIRECT)));
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case CTRL:
            return getOrCreateControlDependency();
        case MFENCE:
            relation = wmm.newRelationWithName(MFENCE);
            wmm.addDefinition(new Fenced(relation,FilterBasic.get(MFENCE)));
            return relation;
        case ISH:
            relation = wmm.newRelationWithName(ISH);
            wmm.addDefinition(new Fenced(relation,FilterBasic.get(ISH)));
            return relation;
        case CTRLISB:
            relation = wmm.newRelationWithName(CTRLISB);
            child = wmm.newRelation();
            filter = getOrCreateControlDependency();
            wmm.addDefinition(new Fenced(child,FilterBasic.get(ISB)));
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        case SYNC:
            relation = wmm.newRelationWithName(SYNC);
            wmm.addDefinition(new Fenced(relation,FilterBasic.get(SYNC)));
            return relation;
        case LWSYNC:
            relation = wmm.newRelationWithName(LWSYNC);
            wmm.addDefinition(new Fenced(relation,FilterBasic.get(LWSYNC)));
            return relation;
        case CTRLISYNC:
            relation = wmm.newRelationWithName(CTRLISYNC);
            child = wmm.newRelation();
            filter = getOrCreateControlDependency();
            wmm.addDefinition(new Fenced(child,FilterBasic.get(ISYNC)));
            wmm.addDefinition(new Intersection(relation,child,filter));
            return relation;
        default:
            return FilterBasic.get(name);
        }
    }
}