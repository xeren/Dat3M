package com.dat3m.dartagnan.wmm.relation.unary;

import com.dat3m.dartagnan.wmm.relation.Relation;

/**
 *
 * @author Florian Furbach
 */
public class RelTransRef extends RelTrans {

    public static String makeTerm(Relation r1){
        return r1.getName() + "^*";
    }

    public RelTransRef(Relation r1) {
        super(true,r1);
        term = makeTerm(r1);
    }

    public RelTransRef(Relation r1, String name) {
        super(true,r1,name);
        term = makeTerm(r1);
    }
}