package com.dat3m.dartagnan.wmm.utils;

import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.Relation;
import com.dat3m.dartagnan.wmm.relation.RelationNameRepository;
import com.dat3m.dartagnan.wmm.relation.base.RelCrit;
import com.dat3m.dartagnan.wmm.relation.base.RelRMW;
import com.dat3m.dartagnan.wmm.relation.base.local.RelAddrDirect;
import com.dat3m.dartagnan.wmm.relation.base.local.RelCASDep;
import com.dat3m.dartagnan.wmm.relation.base.local.RelIdd;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelCo;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelLoc;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelRf;
import com.dat3m.dartagnan.wmm.relation.base.stat.*;
import com.dat3m.dartagnan.wmm.relation.binary.*;
import com.dat3m.dartagnan.wmm.relation.unary.RelInverse;
import com.dat3m.dartagnan.wmm.relation.unary.RelTrans;
import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.dat3m.dartagnan.wmm.relation.RelationNameRepository.*;

public class RelationRepository {

    private final Map<String, Relation> relationMap = new HashMap<>();

    public Set<Relation> getRelations(){
        Set<Relation> set = new HashSet<>();
        for(Map.Entry<String, Relation> entry : relationMap.entrySet()){
            if(!entry.getValue().getIsNamed() || entry.getValue().getName().equals(entry.getKey())){
                set.add(entry.getValue());
            }
        }
        return set;
    }

    public Relation getRelation(String name){
        Relation relation = relationMap.get(name);
        if(relation == null){
            if(RelationNameRepository.contains(name)){
                relation = getBasicRelation(name);
                addRelation(relation);
            }
        }
        return relation;
    }

    public void updateRelation(Relation relation){
        if(relation.getIsNamed()){
            String name = relation.getName();
            Preconditions.checkState(!relationMap.containsKey(name), "Relation " + name + " is already declared");
            relationMap.put(name, relation);
        }
    }

    public void addRelation(Relation relation) {
        relationMap.put(relation.getTerm(), relation);
        if(relation.getIsNamed()){
            relationMap.put(relation.getName(), relation);
        }
    }

    public void addAlias(String alias, Relation relation) {
        relationMap.put(alias, relation);
    }

    public boolean containsRelation(String name) {
        return relationMap.containsKey(name);
    }

    private Relation getBasicRelation(String name){
        Preconditions.checkArgument(RelationNameRepository.contains(name), name + " is not listed in RelationNameRepository.");
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
            case CASDEP:
                return new RelCASDep();
            case CRIT:
                return new RelCrit();
            case IDD:
                return new RelIdd();
            case ADDRDIRECT:
                return new RelAddrDirect();
            case CTRLDIRECT:
                return new RelCtrlDirect();
            case EMPTY:
                return new RelEmpty();
            case RFINV:
                return new RelInverse(getRelation(RF));
            case FR:
                return new RelComposition(getRelation(RFINV), getRelation(CO)).setName(FR);
            case MM:
                return new RelCartesian(FilterBasic.get(Tag.MEMORY), FilterBasic.get(Tag.MEMORY));
            case MV:
                return new RelCartesian(FilterBasic.get(Tag.MEMORY), FilterBasic.get(Tag.VISIBLE));
            case IDDTRANS:
                return new RelTrans(getRelation(IDD));
            case DATA:
                return new RelIntersection(getRelation(IDDTRANS), getRelation(MM)).setName(DATA);
            case ADDR: {
                Relation addrdirect = getRelation(ADDRDIRECT);
                RelComposition comp = new RelComposition(getRelation(IDDTRANS), addrdirect);
                addRelation(comp);
                RelUnion union = new RelUnion(addrdirect, comp);
                addRelation(union);
                return new RelIntersection(union, getRelation(MM)).setName(ADDR);
            }
            case CTRL: {
                RelComposition comp = new RelComposition(getRelation(IDDTRANS), getRelation(CTRLDIRECT));
                addRelation(comp);
                return new RelIntersection(comp, getRelation(MV)).setName(CTRL);
            }
            case POLOC:
                return new RelIntersection(getRelation(PO), getRelation(LOC)).setName(POLOC);
            case RFE:
                return new RelIntersection(getRelation(RF), getRelation(EXT)).setName(RFE);
            case RFI:
                return new RelIntersection(getRelation(RF), getRelation(INT)).setName(RFI);
            case COE:
                return new RelIntersection(getRelation(CO), getRelation(EXT)).setName(COE);
            case COI:
                return new RelIntersection(getRelation(CO), getRelation(INT)).setName(COI);
            case FRE:
                return new RelIntersection(getRelation(FR), getRelation(EXT)).setName(FRE);
            case FRI:
                return new RelIntersection(getRelation(FR), getRelation(INT)).setName(FRI);
            case MFENCE:
                return new RelFencerel(MFENCE);
            case ISH:
                return new RelFencerel(ISH);
            case ISB:
                return new RelFencerel(ISB);
            case SYNC:
                return new RelFencerel(SYNC);
            case ISYNC:
                return new RelFencerel(ISYNC);
            case LWSYNC:
                return new RelFencerel(LWSYNC);
            case CTRLISYNC:
                return new RelIntersection(getRelation(CTRL), getRelation(ISYNC)).setName(CTRLISYNC);
            case CTRLISB:
                return new RelIntersection(getRelation(CTRL), getRelation(ISB)).setName(CTRLISB);
            default:
                throw new RuntimeException(name + "is part of RelationNameRepository but it has no associated relation.");
        }
    }
}
