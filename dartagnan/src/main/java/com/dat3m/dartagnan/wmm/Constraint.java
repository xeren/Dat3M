package com.dat3m.dartagnan.wmm;

import com.dat3m.dartagnan.program.filter.FilterAbstract;
import com.dat3m.dartagnan.utils.dependable.Dependent;
import com.dat3m.dartagnan.wmm.axiom.Axiom;
import com.dat3m.dartagnan.wmm.relation.Relation;

public interface Constraint extends Dependent<Relation> {

    <T> T visit(Visitor<? extends T> visitor);

    interface Visitor <T> {
        default T visitEmptiness(Axiom ax) { return null; }
        default T visitIrreflexivity(Axiom ax) { return null; }
        default T visitAcyclicity(Axiom ax) { return null; }
        default T visitForceEncoding(Axiom ax) { return null; }
        default T visitUnion(Relation rel, Relation r1, Relation r2) { return null; }
        default T visitIntersection(Relation rel, Relation r1, Relation r2) { return null; }
        default T visitDifference(Relation rel, Relation subtrahent, Relation minuent) { return null; }
        default T visitComposition(Relation rel, Relation r1, Relation r2) { return null; }
        default T visitDomainIdentity(Relation rel, Relation operand) { return null; }
        default T visitRangeIdentity(Relation rel, Relation operand) { return null; }
        default T visitInverse(Relation rel, Relation operand) { return null; }
        default T visitRecursive(Relation rel, Relation other) { return null; }
        default T visitTransitiveClosure(Relation rel, Relation operand) { return null; }
        default T visitReflexiveTransitiveClosure(Relation rel, Relation operand) { return null; }
        default T visitCriticalSections(Relation rel) { return null; }
        default T visitReadModifyWrites(Relation rel) { return null; }
        default T visitProduct(Relation rel, FilterAbstract domain, FilterAbstract range) { return null; }
        default T visitEmpty(Relation rel) { return null; }
        default T visitIdentity(Relation rel, FilterAbstract set) { return null; }
        default T visitExternal(Relation rel) { return null; }
        default T visitInternal(Relation rel) { return null; }
        default T visitProgramOrder(Relation rel) { return null; }
        default T visitControl(Relation rel) { return null; }
        default T visitFences(Relation rel, FilterAbstract fence) { return null; }
        default T visitInternalDataDependency(Relation rel) { return null; }
        default T visitCompareAndSwapDependency(Relation rel) { return null; }
        default T visitAddressDependency(Relation rel) { return null; }
        default T visitMemoryOrder(Relation rel) { return null; }
        default T visitSameAddress(Relation rel) { return null; }
        default T visitReadFrom(Relation rel) { return null; }
    }
}
