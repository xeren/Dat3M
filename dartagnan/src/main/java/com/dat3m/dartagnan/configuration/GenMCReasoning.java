package com.dat3m.dartagnan.configuration;

public enum GenMCReasoning implements OptionInterface {
    NONE, REASONS, PATTERNS, BOTH;

    public static GenMCReasoning getDefault() {
        return BOTH;
    }

    public final boolean usesReasons() {
        return this == REASONS || this == BOTH;
    }

    public final boolean usesPatterns() {
        return this == PATTERNS || this == BOTH;
    }

    public final boolean usesReasoning() {
        return this != NONE;
    }
}
