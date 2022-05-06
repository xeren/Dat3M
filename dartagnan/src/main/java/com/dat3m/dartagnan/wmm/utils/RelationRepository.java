package com.dat3m.dartagnan.wmm.utils;

import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.RecursiveRelation;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.wmm.relation.base.RelCrit;
import com.dat3m.dartagnan.wmm.relation.base.RelRMW;
import com.dat3m.dartagnan.wmm.relation.base.local.RelAddrDirect;
import com.dat3m.dartagnan.wmm.relation.base.local.RelIdd;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelCo;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelLoc;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelRf;
import com.dat3m.dartagnan.wmm.relation.base.stat.*;
import com.dat3m.dartagnan.wmm.relation.binary.*;
import com.dat3m.dartagnan.wmm.relation.unary.*;
import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.*;

public class RelationRepository {

    private final Map<String, Relation> relationMap = new HashMap<>();
    private final Map<String,Relation> fence = new HashMap<>();
    private final HashMap<FilterAbstract,Relation> identity = new HashMap<>();
    private final HashMap<FilterAbstract,HashMap<FilterAbstract,Relation>> cartesian = new HashMap<>();
    private final HashMap<Relation,Relation> inverse = new HashMap<>();
    private final HashMap<Relation,Relation> domain = new HashMap<>();
    private final HashMap<Relation,Relation> range = new HashMap<>();
    private final HashMap<Relation,Relation> transitive = new HashMap<>();
    private final HashMap<Relation,HashMap<Relation,Relation>> union = new HashMap<>();
    private final HashMap<Relation,HashMap<Relation,Relation>> intersection = new HashMap<>();
    private final HashMap<Relation,HashMap<Relation,Relation>> difference = new HashMap<>();
    private final HashMap<Relation,HashMap<Relation,Relation>> composition = new HashMap<>();

    public Collection<Relation> getRelations(){
        return relationMap.values();
    }

    public Relation getRelation(String name){
        Relation relation = relationMap.get(name);
        if(relation != null) {
            return relation;
        }
        Relation basic = getBasicRelation(name);
        if(basic != null) {
            relationMap.put(basic.getTerm(), basic);
            if(basic.getIsNamed()){
                relationMap.put(basic.getName(), basic);
            }
        }
        return basic;
    }

    public void nameRelation(Relation relation, String name) {
        Preconditions.checkArgument(!relationMap.containsKey(name),"Name {} already defined",name);
        relationMap.put(name,relation);
        relation.setName(name);
    }

    public RecursiveRelation recursive(String name) {
        RecursiveRelation relation = new RecursiveRelation(name);
        nameRelation(relation,name);
        return relation;
    }

    public Relation fence(String type) {
        Relation relation = fence.computeIfAbsent(type,RelFencerel::new);
        put(relation);
        return relation;
    }

    public Relation identity(FilterAbstract domain) {
        Relation relation = identity.computeIfAbsent(domain,RelSetIdentity::new);
        put(relation);
        return relation;
    }

    public Relation cartesian(FilterAbstract domain, FilterAbstract range) {
        Relation relation = cartesian.computeIfAbsent(domain,k->new HashMap<>()).computeIfAbsent(range,k->new RelCartesian(domain,range));
        put(relation);
        return relation;
    }

    public Relation inverse(Relation child) {
        Relation relation = inverse.computeIfAbsent(child,RelInverse::new);
        put(relation);
        return relation;
    }

    public Relation domain(Relation child) {
        Relation relation = domain.computeIfAbsent(child, RelDomainIdentity::new);
        put(relation);
        return relation;
    }

    public Relation range(Relation child) {
        Relation relation = range.computeIfAbsent(child,RelRangeIdentity::new);
        put(relation);
        return relation;
    }

    public Relation transitive(Relation child) {
        Relation relation = transitive.computeIfAbsent(child,RelTrans::new);
        put(relation);
        return relation;
    }

    public Relation union(Relation first, Relation second) {
        Relation relation = union.computeIfAbsent(first,k->new HashMap<>()).computeIfAbsent(second,k->new RelUnion(first,second));
        put(relation);
        union.computeIfAbsent(second,k->new HashMap<>()).put(first,relation);
        return relation;
    }

    public Relation intersection(Relation first, Relation second) {
        Relation relation = intersection.computeIfAbsent(first,k->new HashMap<>()).computeIfAbsent(second,k->new RelIntersection(first,second));
        put(relation);
        intersection.computeIfAbsent(second,k->new HashMap<>()).put(first,relation);
        return relation;
    }

    public Relation difference(Relation first, Relation second) {
        Relation relation = difference.computeIfAbsent(first,k->new HashMap<>()).computeIfAbsent(second,k->new RelMinus(first,second));
        put(relation);
        return relation;
    }

    public Relation composition(Relation first, Relation second) {
        Relation relation = composition.computeIfAbsent(first,k->new HashMap<>()).computeIfAbsent(second,k->new RelComposition(first,second));
        put(relation);
        return relation;
    }

    public boolean containsRelation(String name) {
        return relationMap.containsKey(name);
    }

    private void put(Relation relation) {
        relationMap.put(relation.getTerm(),relation);
    }

    private Relation getBasicRelation(String name){
        switch (name){
            case POWITHLOCALEVENTS:
                return new RelPo(true);
            case PO:
                return new RelPo();
            case LOC:
                return new RelLoc();
            case ID:
                return new RelId();
            case INT:
                return new RelInt();
            case EXT:
                return new RelExt();
            case CO:
                return new RelCo();
            case RF:
                return new RelRf();
            case RMW:
                return new RelRMW();
            case CRIT:
                return new RelCrit();
            case IDD:
                return new RelIdd();
            case ADDRDIRECT:
                return new RelAddrDirect();
            case CTRLDIRECT:
                return new RelCtrlDirect();
            case EMPTY:
                return new RelEmpty(EMPTY);
            case RFINV:
                return inverse(getRelation(RF));
            case FR:
                return composition(getRelation(RFINV), getRelation(CO)).setName(FR);
            case RW:
                return cartesian(FilterBasic.get(Tag.READ), FilterBasic.get(Tag.WRITE));
            case RM:
                return cartesian(FilterBasic.get(Tag.READ), FilterBasic.get(Tag.MEMORY));
            case RV:
                return cartesian(FilterBasic.get(Tag.READ), FilterBasic.get(Tag.VISIBLE));
            case IDDTRANS:
                return transitive(getRelation(IDD));
            case DATA:
                return intersection(getRelation(IDDTRANS), getRelation(RW)).setName(DATA);
            case ADDR:
                return intersection(union(getRelation(ADDRDIRECT),composition(getRelation(IDDTRANS),getRelation(ADDRDIRECT))),getRelation(RM)).setName(ADDR);
            case CTRL:
                return intersection(composition(getRelation(IDDTRANS),getRelation(CTRLDIRECT)),getRelation(RV)).setName(CTRL);
            case POLOC:
                return intersection(getRelation(PO), getRelation(LOC)).setName(POLOC);
            case RFE:
                return intersection(getRelation(RF), getRelation(EXT)).setName(RFE);
            case RFI:
                return intersection(getRelation(RF), getRelation(INT)).setName(RFI);
            case COE:
                return intersection(getRelation(CO), getRelation(EXT)).setName(COE);
            case COI:
                return intersection(getRelation(CO), getRelation(INT)).setName(COI);
            case FRE:
                return intersection(getRelation(FR), getRelation(EXT)).setName(FRE);
            case FRI:
                return intersection(getRelation(FR), getRelation(INT)).setName(FRI);
            case MFENCE:
                return fence(MFENCE);
            case ISH:
                return fence(ISH);
            case ISB:
                return fence(ISB);
            case SYNC:
                return fence(SYNC);
            case ISYNC:
                return fence(ISYNC);
            case LWSYNC:
                return fence(LWSYNC);
            case CTRLISYNC:
                return intersection(getRelation(CTRL), getRelation(ISYNC)).setName(CTRLISYNC);
            case CTRLISB:
                return intersection(getRelation(CTRL), getRelation(ISB)).setName(CTRLISB);
            default:
                return null;
        }
    }
}
