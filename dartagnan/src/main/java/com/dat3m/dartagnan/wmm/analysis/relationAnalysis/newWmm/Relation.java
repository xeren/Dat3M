package com.dat3m.dartagnan.wmm.analysis.relationAnalysis.newWmm;

import com.dat3m.dartagnan.utils.dependable.Dependent;

import java.util.List;

//TODO: This class should be part of the wmm package and not of the relation analysis
public class Relation implements Dependent<Relation> {

    private String name;
    private int id;
    private DefiningConstraint definingConstraint;

    @Override
    public List<Relation> getDependencies() {
        List<Relation> rels = definingConstraint.getConstrainedRelations();
        return rels.subList(0, rels.size() - 1);
    }

    public DefiningConstraint getDefiningConstraint() { return definingConstraint; }
    public void setDefiningConstraint(DefiningConstraint constr) {
        this.definingConstraint = constr;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getId() { return id;  }
    public void setId(int id) { this.id = id; }

    public boolean isRecursive() { return false; }

}
