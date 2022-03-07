package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.utils.dependable.Dependent;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.Knowledge;
import com.dat3m.dartagnan.wmm.analysis.relationAnalysis.example.Undefined;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.google.common.base.Preconditions;
import org.sosy_lab.java_smt.api.BooleanFormula;

import java.util.List;

//TODO: This class should be part of the wmm package and not of the relation analysis
public class Relation implements Dependent<Relation> {

    private final NewWmm wmm;
    private final int id;

    private String name;
    private Definition definition;
    private boolean isRecursive;

    Relation(NewWmm wmm, int id) {
        this.wmm = wmm;
        this.id = id;
        definition = new Undefined(this);
    }


    @Override
    public List<Relation> getDependencies() {
        return getDefinition().getDependencies();
    }

    public Definition getDefinition() { return definition; }
    void setDefinition(Definition constr) {
        Preconditions.checkArgument(constr.getDefinedRelation() == this,
                "The constraint already defines another relation.");
        this.definition = constr;
    }

    public NewWmm getWmm() { return wmm;}

    public String getName() { return name; }
    public boolean isNamed() { return name != null; }
    public String getTerm() { return definition.getTerm(); }
    public String getNameOrTerm() {
        return isNamed() ? name : getTerm();
    }

    public boolean isRecursive() { return isRecursive; }

    void setName(String name) { this.name = name;}
    void setIsRecursive(boolean isRecursive) { this.isRecursive = isRecursive; }


    public BooleanFormula getSMTVar(Tuple t, Knowledge kRel, EncodingContext ctx) {
        return definition.getSMTVar(t, kRel, ctx);
    }

    @Override
    public String toString() {
        if (isNamed()) {
            return name + " := " + getTerm();
        } else {
            return getTerm();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Relation relation = (Relation) o;
        return id == relation.id && wmm.equals(relation.wmm);
    }

    @Override
    public int hashCode() {
        return id + 31 * wmm.hashCode();
    }
}
