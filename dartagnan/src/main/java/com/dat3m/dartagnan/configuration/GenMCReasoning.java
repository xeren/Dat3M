package com.dat3m.dartagnan.configuration;

public enum GenMCReasoning implements OptionInterface {
    NONE, REASONS, PATTERNS;

    public static GenMCReasoning getDefault() {
        return PATTERNS;
    }
}
