package com.dat3m.dartagnan.wmm.definition;

import com.dat3m.dartagnan.encoding.EncodingContext;
import com.dat3m.dartagnan.wmm.Definition;
import com.dat3m.dartagnan.wmm.Relation;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import org.sosy_lab.java_smt.api.BooleanFormula;

import static com.google.common.base.Preconditions.checkNotNull;

public class Inverse extends Definition {

    private final Relation r1;

    public Inverse(Relation r0, Relation r1) {
        super(r0, "%s^-1");
        this.r1 = checkNotNull(r1);
    }

    @Override
    public <T> T accept(Visitor<? extends T> v) {
        return v.visitInverse(definedRelation, r1);
    }

    @Override
    public BooleanFormula getSMTVar(Tuple edge, EncodingContext c) {
        return r1.getSMTVar(edge.getInverse(), c);
    }
}