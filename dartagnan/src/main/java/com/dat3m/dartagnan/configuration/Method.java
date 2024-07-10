package com.dat3m.dartagnan.configuration;

import java.util.Arrays;

public enum Method implements OptionInterface {
    EAGER, LAZY, GENMC;

    // Used for options in the console
    @Override
    public String asStringOption() {
        return switch (this) {
            case EAGER -> "eager";
            case LAZY -> "lazy";
            case GENMC -> "GenMC";
        };
    }

    // Used to display in UI
    @Override
    public String toString() {
        return switch (this) {
            case EAGER -> "Eager analysis";
            case LAZY -> "Lazy analysis";
            case GENMC -> "GenMC-based analysis";
        };
    }

    public static Method getDefault() {
        return GENMC;
    }

    // Used to decide the order shown by the selector in the UI
    public static Method[] orderedValues() {
        Method[] order = { LAZY, EAGER, GENMC };
        // Be sure no element is missing
        assert (Arrays.asList(order).containsAll(Arrays.asList(values())));
        return order;
    }
}