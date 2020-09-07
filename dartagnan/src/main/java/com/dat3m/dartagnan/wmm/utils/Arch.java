package com.dat3m.dartagnan.wmm.utils;

public enum Arch {
    /**
     * No particular instruction set.
     */
    NONE,
    /**
     * Acorn RISC Machine specific instruction set.
     */
    ARM,
    /**
     * Power PC specific instruction set.
     */
    POWER,
    /**
     * X86 specific instruction set.
     */
    TSO;

    public static Arch get(String arch){
        if(arch != null){
            arch = arch.trim();
            switch(arch){
                case "none":
                    return NONE;
                case "arm":
                case "arm8":
                    return ARM;
                case "power":
                    return POWER;
                case "tso":
                    return TSO;
            }
        }
        throw new UnsupportedOperationException("Unrecognized architecture " + arch);
    }

    @Override
    public String toString() {
        switch(this){
            case NONE:
                return "none";
            case ARM:
                return "arm";
            case POWER:
                return "power";
            case TSO:
                return "tso";
        }
        return super.toString();
    }
}
