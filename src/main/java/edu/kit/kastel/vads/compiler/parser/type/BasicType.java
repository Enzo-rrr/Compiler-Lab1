package edu.kit.kastel.vads.compiler.parser.type;

import java.util.Locale;

public enum BasicType implements Type {
    INT,
    BOOL,
    VOID;

    @Override
    public String asString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
