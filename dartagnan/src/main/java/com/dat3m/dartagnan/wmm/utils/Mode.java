package com.dat3m.dartagnan.wmm.utils;

public enum Mode {
    KNASTER, IDL, KLEENE, FO;

    public static Mode get(String mode){
        if(mode != null){
            mode = mode.trim();
            switch(mode){
                case "knastertarski":
                    return KNASTER;
                case "idl":
                    return IDL;
                case "kleene":
                    return KLEENE;
                case "firstorder":
                    return FO;
            }
        }
        throw new UnsupportedOperationException("Illegal mode value");
    }

    @Override
    public String toString() {
        switch(this){
            case KNASTER:
                return "knastertarski";
            case IDL:
                return "idl";
            case KLEENE:
                return "kleene";
            case FO:
                return "firstorder";
        }
        return super.toString();
    }
}
