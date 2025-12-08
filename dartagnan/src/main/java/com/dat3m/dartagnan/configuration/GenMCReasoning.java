package com.dat3m.dartagnan.configuration;

public enum GenMCReasoning implements OptionInterface {
    NONE, REASONS, PATTERNS, BOTH;

    public static GenMCReasoning getDefault() {
        return BOTH;
    }

    public boolean usesReasons() {
        return this == REASONS || this == BOTH;
    }

    public boolean usesPatterns() {
        return this == PATTERNS || this == BOTH;
    }

    public boolean usesReasoning() {
        return this != NONE;
    }
}
