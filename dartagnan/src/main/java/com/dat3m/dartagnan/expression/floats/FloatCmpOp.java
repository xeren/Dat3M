package com.dat3m.dartagnan.expression.floats;

import com.dat3m.dartagnan.expression.ExpressionKind;

public enum FloatCmpOp implements ExpressionKind {
    OEQ, ONEQ, OGT, OGTE, OLT, OLTE, ORD, // Ordered comparisons (always false on NAN)
    UEQ, UNEQ, UGT, UGTE, ULT, ULTE, UNO; // Unordered comparisons (always true on NAN)

    @Override
    public String toString() {
        return getSymbol();
    }

    @Override
    public String getSymbol() {
        return switch (this) {
            case OEQ -> "==";
            case ONEQ -> "!=";
            case OGT -> ">";
            case OGTE -> ">=";
            case OLT -> "<";
            case OLTE -> "<=";
            case ORD -> "ord";
            case UEQ -> "u==";
            case UNEQ -> "u!=";
            case UGT -> "u>";
            case UGTE -> "u>=";
            case ULT -> "u<";
            case ULTE -> "u<=";
            case UNO -> "unord";
        };
    }

    public FloatCmpOp inverted() {
        return switch (this) {
            case OEQ -> UNEQ;
            case ONEQ -> UEQ;
            case OGT -> ULTE;
            case OGTE -> ULT;
            case OLT -> UGTE;
            case OLTE -> UGT;
            case ORD -> UNO;
            case UEQ -> ONEQ;
            case UNEQ -> OEQ;
            case UGT -> OLTE;
            case UGTE -> OLT;
            case ULT -> OGTE;
            case ULTE -> OGT;
            case UNO -> ORD;
        };
    }

}
