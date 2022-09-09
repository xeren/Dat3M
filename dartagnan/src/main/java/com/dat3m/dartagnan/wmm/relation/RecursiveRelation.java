package com.dat3m.dartagnan.wmm.relation;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author Florian Furbach
 */
public class RecursiveRelation extends Relation {

    private Relation r1;

    public Relation getInner() {
        return r1;
    }

    @Override
    public List<Relation> getDependencies() {
        return Collections.singletonList(r1);
    }

    public RecursiveRelation(String name) {
        this.name = name;
    }

    public void setConcreteRelation(Relation r1){
        r1.setName(name);
        this.r1 = r1;
    }

    @Override
    public <T> T visit(Visitor<? extends T> v) {
        return v.visitRecursive(this, r1);
    }

    @Override
    public String getTerm() {
        return r1 == null ? name : r1.getTerm();
    }
}
